package io.modelcontextprotocol.kotlin.sdk.client

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
import io.ktor.http.append
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val logger = KotlinLogging.logger {}

/**
 * Client transport for Streamable HTTP: this will send messages via HTTP POST requests
 * and optionally receive streaming responses via SSE.
 *
 * This implements the Streamable HTTP transport as specified in MCP 2024-11-05.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StreamableHttpClientTransport(
    private val client: HttpClient,
    private val url: String,
    private val requestBuilder: HttpRequestBuilder.() -> Unit = {},
) : AbstractTransport() {

    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private var sseSession: ClientSSESession? = null
    private val scope by lazy { CoroutineScope(SupervisorJob()) }
    private var sseJob: Job? = null
    private var sessionId: String? = null

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StreamableHttpClientTransport already started!")
        }
        logger.debug { "Client transport starting..." }
        startSseSession()
    }

    private suspend fun startSseSession() {
        logger.debug { "Client attempting to start SSE session at url: $url" }
        try {
            sseSession = client.sseSession(
                urlString = url,
                block = requestBuilder,
            )
            logger.debug { "Client SSE session started successfully." }

            sseJob = scope.launch(CoroutineName("StreamableHttpTransport.collect#${hashCode()}")) {
                sseSession?.incoming?.collect { event ->
                    logger.trace { "Client received SSE event: event=${event.event}, data=${event.data}" }
                    when (event.event) {
                        "error" -> {
                            val e = IllegalStateException("SSE error: ${event.data}")
                            logger.error(e) { "SSE stream reported an error event." }
                            _onError(e)
                        }

                        else -> {
                            // All non-error events are treated as JSON-RPC messages
                            try {
                                val eventData = event.data
                                if (!eventData.isNullOrEmpty()) {
                                    val message = McpJson.decodeFromString<JSONRPCMessage>(eventData)
                                    _onMessage(message)
                                }
                            } catch (e: Exception) {
                                logger.error(e) { "Error processing SSE message" }
                                _onError(e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // SSE session is optional, don't fail if it can't be established
            // The server might not support GET requests for SSE
            logger.warn(e) { "Client failed to start SSE session. This may be expected if the server does not support GET." }
            _onError(e)
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        logger.debug { "Client sending message via POST to $url: ${McpJson.encodeToString(message)}" }
        try {
            val response = client.post(url) {
                requestBuilder()
                contentType(ContentType.Application.Json)
                headers.append(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")

                // Add session ID if we have one
                sessionId?.let {
                    headers.append("Mcp-Session-Id", it)
                }

                setBody(McpJson.encodeToString(message))
            }
            logger.debug { "Client received POST response: ${response.status}" }

            if (!response.status.isSuccess()) {
                val text = response.bodyAsText()
                val error = Exception("HTTP ${response.status}: $text")
                logger.error(error) { "Client POST request failed." }
                _onError(error)
                throw error
            }

            // Extract session ID from response headers if present
            response.headers["Mcp-Session-Id"]?.let {
                sessionId = it
            }

            // Handle response based on content type
            when (response.contentType()?.contentType) {
                ContentType.Application.Json.contentType -> {
                    // Single JSON response
                    val responseBody = response.bodyAsText()
                    logger.trace { "Client processing JSON response: $responseBody" }
                    if (responseBody.isNotEmpty()) {
                        try {
                            val responseMessage = McpJson.decodeFromString<JSONRPCMessage>(responseBody)
                            _onMessage(responseMessage)
                        } catch (e: Exception) {
                            logger.error(e) { "Error processing JSON response" }
                            _onError(e)
                        }
                    }
                }

                ContentType.Text.EventStream.contentType -> {
                    logger.trace { "Client received SSE stream in POST response. Messages will be handled by the main SSE session." }
                }

                else -> {
                    logger.trace { "Client received response with unexpected or no content type: ${response.contentType()}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Client send failed." }
            _onError(e)
            throw e
        }
    }

    override suspend fun close() {
        if (!initialized.load()) {
            return // Already closed or never started
        }
        logger.debug { "Client transport closing." }

        try {
            sseSession?.cancel()
            sseJob?.cancelAndJoin()
            scope.cancel()
        } catch (e: Exception) {
            // Ignore errors during cleanup
        } finally {
            _onClose()
        }
    }
}