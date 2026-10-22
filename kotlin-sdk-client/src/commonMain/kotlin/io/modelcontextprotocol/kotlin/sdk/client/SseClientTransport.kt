package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.append
import io.ktor.http.hostWithPortIfSpecified
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.ktor.http.protocolWithAuthority
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration

/**
 * Client transport for SSE: this will connect to a server using Server-Sent Events for receiving
 * messages and make separate POST requests for sending messages.
 */
@Deprecated(
    "The SSE transport is deprecated in the MCP specification (SEP-2596) " +
        "and will be removed in a future release. Use StreamableHttpClientTransport instead.",
    level = DeprecationLevel.WARNING,
)
@OptIn(ExperimentalAtomicApi::class)
public class SseClientTransport(
    private val client: HttpClient,
    private val urlString: String?,
    private val reconnectionTime: Duration? = null,
    private val requestBuilder: HttpRequestBuilder.() -> Unit = {},
) : AbstractClientTransport() {

    override val logger: KLogger = KotlinLogging.logger {}

    private val endpoint = CompletableDeferred<String>()

    private lateinit var session: ClientSSESession
    private lateinit var scope: CoroutineScope
    private var job: Job? = null

    override suspend fun initialize() {
        session = urlString?.let {
            client.sseSession(
                urlString = it,
                reconnectionTime = reconnectionTime,
                block = requestBuilder,
            )
        } ?: client.sseSession(
            reconnectionTime = reconnectionTime,
            block = requestBuilder,
        )

        // Endpoints are validated against the origin of the SSE request, so that request must not
        // have been redirected away from the origin the transport was configured with.
        val requestedUrl = urlString?.let(::parseUrl)
        val connectionUrl = session.call.request.url
        if (requestedUrl != null) {
            check(requestedUrl.hasSameOrigin(connectionUrl)) {
                "SSE request to ${requestedUrl.safeOrigin} was redirected to a different origin " +
                    connectionUrl.safeOrigin
            }
        }

        scope = CoroutineScope(session.coroutineContext + SupervisorJob())

        job = scope.launch(CoroutineName("SseMcpClientTransport.connect#${hashCode()}")) {
            collectMessages()
        }

        endpoint.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
        check(job?.isActive == true) { "SseClientTransport is closed!" }
        check(endpoint.isCompleted) { "Not connected!" }

        val response = client.post(endpoint.getCompleted()) {
            requestBuilder()
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(McpJson.encodeToString(message))
        }

        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            error("Error POSTing to endpoint (HTTP ${response.status}): $text")
        }

        logger.debug { "Client successfully sent message via SSE $endpoint" }
    }

    private suspend fun CoroutineScope.collectMessages() {
        try {
            session.incoming.collect { event ->
                this@collectMessages.ensureActive()

                when (event.event) {
                    "error" -> {
                        val error = IllegalStateException("SSE error: ${event.data}")
                        _onError(error)
                        throw error
                    }

                    "open" -> {
                        // The connection is open, but we need to wait for the endpoint to be received.
                    }

                    "endpoint" -> handleEndpoint(event.data.orEmpty())

                    else -> handleMessage(event.data.orEmpty())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _onError(e)
            throw e
        } finally {
            closeResources()
            invokeOnCloseCallback()
        }
    }

    private fun handleEndpoint(eventData: String) {
        val endpointUrl = try {
            session.call.request.url.resolveEndpoint(eventData)
        } catch (e: IllegalArgumentException) {
            _onError(e)
            endpoint.completeExceptionally(e)
            return
        }
        endpoint.complete(endpointUrl)
        logger.debug { "Client connected to endpoint: $endpointUrl" }
    }

    private suspend fun handleMessage(data: String) {
        try {
            val message = McpJson.decodeFromString<JSONRPCMessage>(data)
            _onMessage(message)
        } catch (e: SerializationException) {
            _onError(e)
        }
    }

    override suspend fun closeResources() {
        withContext(NonCancellable) {
            job?.cancel()
            try {
                if (::session.isInitialized) session.cancel()
                if (::scope.isInitialized) scope.cancel()
                endpoint.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _onError(e)
            }
        }
    }
}

/**
 * Compares origins as scheme, host (case-insensitive) and effective port, so an explicit default port
 * matches an omitted one. User info is not part of the origin.
 */
private fun Url.hasSameOrigin(other: Url): Boolean =
    protocol.name == other.protocol.name && host.equals(other.host, ignoreCase = true) && port == other.port

/** Scheme, host and non-default port, without user info, so it is safe to put into error messages. */
private val Url.safeOrigin: String
    get() = "${protocol.name}://$hostWithPortIfSpecified"

/**
 * Resolves [reference] from an `endpoint` event against this SSE request URL.
 * A full URL must have the same origin, and a query-only reference keeps the path (RFC 3986, section 5.2.2).
 */
private fun Url.resolveEndpoint(reference: String): String {
    if (reference.startsWith("http://") || reference.startsWith("https://")) {
        val url = requireNotNull(parseUrl(reference)) { "Endpoint is not a valid URL" }
        require(url.hasSameOrigin(this)) {
            "Endpoint origin ${url.safeOrigin} does not match connection origin $safeOrigin"
        }
        return reference
    }
    val pathAndQuery = when {
        reference.startsWith("/") -> reference
        reference.startsWith("?") -> encodedPath.ifEmpty { "/" } + reference
        else -> encodedPath.substringBeforeLast('/', missingDelimiterValue = "") + "/" + reference
    }
    return protocolWithAuthority + pathAndQuery
}
