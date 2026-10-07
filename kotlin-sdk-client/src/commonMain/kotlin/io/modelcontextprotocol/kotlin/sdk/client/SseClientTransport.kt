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
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.append
import io.ktor.http.encodedPath
import io.ktor.http.hostWithPortIfSpecified
import io.ktor.http.isSuccess
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

/** A reference that starts with a scheme is an absolute URI (RFC 3986, section 3.1). */
private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/** A scheme-prefixed or network-path reference with a non-empty authority (RFC 3986, section 3.2). */
private val URI_WITH_AUTHORITY = Regex("^([A-Za-z][A-Za-z0-9+.-]*:)?//[^/?#]")

/**
 * Client transport for SSE: this will connect to a server using Server-Sent Events for receiving
 * messages and make separate POST requests for sending messages.
 */
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

    private val origin: String by lazy {
        session.call.request.url.protocolWithAuthority
    }

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
        val requestedUrl = urlString?.let { Url(it) }?.takeIf { it.host.isNotEmpty() }
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
                ensureActive()

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

    /**
     * Resolves and completes [endpoint] based on [eventData].
     * Resolves the reference against the SSE request URL as described in RFC 3986, section 5.2,
     * and rejects the result if its origin differs from the SSE connection origin.
     */
    private fun handleEndpoint(eventData: String) {
        try {
            val connectionUrl = session.call.request.url
            val hasSchemeOrAuthority = URI_SCHEME.containsMatchIn(eventData) || eventData.startsWith("//")
            if (hasSchemeOrAuthority && !URI_WITH_AUTHORITY.containsMatchIn(eventData)) {
                rejectEndpoint("Endpoint URI with a scheme or authority must have a non-empty authority")
                return
            }
            val endpointUrl = when {
                URI_SCHEME.containsMatchIn(eventData) -> eventData
                eventData.startsWith("//") -> "${connectionUrl.protocol.name}:$eventData"
                else -> origin + connectionUrl.resolvePathAndQuery(eventData)
            }
            val url = Url(endpointUrl)
            if (!url.hasSameOrigin(connectionUrl)) {
                rejectEndpoint(
                    "Endpoint origin ${url.safeOrigin} does not match connection origin ${connectionUrl.safeOrigin}",
                )
                return
            }
            endpoint.complete(url.withoutDotSegments() ?: endpointUrl)
            logger.debug { "Client connected to endpoint: $endpointUrl" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _onError(e)
            endpoint.completeExceptionally(e)
            throw e
        }
    }

    private fun rejectEndpoint(message: String) {
        val error = IllegalArgumentException(message)
        _onError(error)
        endpoint.completeExceptionally(error)
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
 * Resolves a reference without scheme and authority against this URL (RFC 3986, section 5.2.2)
 * and returns the resulting path and query. The fragment is dropped.
 */
private fun Url.resolvePathAndQuery(reference: String): String {
    val withoutFragment = reference.substringBefore('#')
    val referencePath = withoutFragment.substringBefore('?')
    val referenceQuery = if ('?' in withoutFragment) withoutFragment.substringAfter('?') else null
    val path = when {
        referencePath.isEmpty() -> encodedPath

        referencePath.startsWith("/") -> removeDotSegments(referencePath)

        else -> removeDotSegments(
            encodedPath.substringBeforeLast('/', missingDelimiterValue = "") + "/" + referencePath,
        )
    }
    val baseQuery = encodedQuery.takeIf { it.isNotEmpty() }
    val query = if (referencePath.isEmpty()) referenceQuery ?: baseQuery else referenceQuery
    return if (query == null) path else "$path?$query"
}

/**
 * Returns this URL with `.` and `..` segments removed from its path (RFC 3986, section 5.2.2),
 * or `null` if the path has none.
 */
private fun Url.withoutDotSegments(): String? {
    val path = encodedPath
    if (path.isEmpty()) return null
    val normalized = removeDotSegments(path)
    return if (normalized == path) null else URLBuilder(this).apply { encodedPath = normalized }.buildString()
}

/** Removes `.` and `..` segments from an absolute [path] (RFC 3986, section 5.2.4). */
private fun removeDotSegments(path: String): String {
    val output = ArrayDeque<String>()
    val segments = path.removePrefix("/").split('/')
    segments.forEachIndexed { index, segment ->
        val isLast = index == segments.lastIndex
        when (segment) {
            "." -> if (isLast) output.addLast("")

            ".." -> {
                output.removeLastOrNull()
                if (isLast) output.addLast("")
            }

            else -> output.addLast(segment)
        }
    }
    return output.joinToString("/", prefix = "/")
}
