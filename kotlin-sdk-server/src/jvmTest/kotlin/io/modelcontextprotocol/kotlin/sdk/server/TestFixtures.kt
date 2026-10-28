package io.modelcontextprotocol.kotlin.sdk.server

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sse.ServerSSESession
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.readLine
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlin.coroutines.CoroutineContext

internal fun testServer(): Server = Server(
    serverInfo = Implementation(name = "test-server", version = "1.0.0"),
    options = ServerOptions(capabilities = ServerCapabilities()),
)

internal fun initializeRequest(): JSONRPCRequest = InitializeRequest(
    InitializeRequestParams(
        protocolVersion = LATEST_PROTOCOL_VERSION,
        capabilities = ClientCapabilities(),
        clientInfo = Implementation(name = "test-client", version = "1.0.0"),
    ),
).toJSON()

/** The `Accept` and `Content-Type` headers a Streamable HTTP client sends with every POST. */
internal fun HttpRequestBuilder.streamableHeaders() {
    header(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Text.EventStream}")
    contentType(ContentType.Application.Json)
}

/** Reads the session id announced by the `endpoint` event of an MCP SSE stream. */
internal suspend fun HttpResponse.readSseSessionId(): String? {
    val channel = bodyAsChannel()
    var eventName: String? = null
    while (true) {
        val line = channel.readLine() ?: return null
        when {
            line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()

            line.startsWith("data:") && eventName == "endpoint" ->
                return line.substringAfter("sessionId=", missingDelimiterValue = "").ifEmpty { null }
        }
    }
}

/** No-op [ServerSSESession] that lets a plain route drive a transport that expects an SSE session. */
internal class FakeServerSSESession(
    override val call: ApplicationCall,
    override val coroutineContext: CoroutineContext,
) : ServerSSESession {
    override suspend fun send(event: ServerSentEvent) {}
    override suspend fun close() {}
}
