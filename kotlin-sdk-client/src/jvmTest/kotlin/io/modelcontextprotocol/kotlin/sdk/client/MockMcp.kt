package io.modelcontextprotocol.kotlin.sdk.client

import dev.mokksy.mokksy.Mokksy
import dev.mokksy.mokksy.StubConfiguration
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.sse.ServerSentEvent
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import kotlinx.coroutines.flow.Flow

/**
 * High-level helper for simulating an MCP server over Streaming HTTP transport with Server-Sent Events (SSE),
 * built on top of an HTTP server using the [Mokksy](https://mokksy.dev) library.
 *
 * Provides test utilities to mock server behavior based on specific request conditions.
 *
 * @param verbose Whether to print detailed logs. Defaults to `false`.
 * @author Konstantin Pavlov
 */
internal class MockMcp(verbose: Boolean = false) {

    private val mokksy: Mokksy = Mokksy(verbose = verbose)

    fun checkForUnmatchedRequests() {
        mokksy.checkForUnmatchedRequests()
    }

    val url = mokksy.baseUrl() + "/mcp"

    @Suppress("LongParameterList")
    fun onJSONRPCRequest(
        httpMethod: HttpMethod = HttpMethod.Post,
        jsonRpcMethod: String,
        expectedSessionId: String? = null,
        sessionId: String,
        contentType: ContentType = ContentType.Application.Json,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        bodyBuilder: () -> String,
    ) {
        mokksy.method(
            configuration = StubConfiguration(removeAfterMatch = true),
            httpMethod = httpMethod,
            requestType = JSONRPCRequest::class,
        ) {
            path("/mcp")
            expectedSessionId?.let {
                containsHeader("Mcp-Session-Id", it)
            }
            bodyMatchesPredicates(
                {
                    it!!.method == jsonRpcMethod
                },
                {
                    it!!.jsonrpc == "2.0"
                },
            )
        } respondsWith {
            body = bodyBuilder.invoke()
            this.contentType = contentType
            headers += "Mcp-Session-Id" to sessionId
            httpStatus = statusCode
        }
    }

    fun onSubscribeWithGet(sessionId: String, block: () -> Flow<ServerSentEvent>) {
        mokksy.get(name = "MCP GETs", requestType = Any::class) {
            path("/mcp")
            containsHeader("Mcp-Session-Id", sessionId)
            containsHeader("Accept", "application/json,text/event-stream")
            containsHeader("Cache-Control", "no-store")
        } respondsWithSseStream {
            headers += "Mcp-Session-Id" to sessionId
            this.flow = block.invoke()
        }
    }

    fun mockUnsubscribeRequest(sessionId: String) {
        mokksy.delete(
            configuration = StubConfiguration(removeAfterMatch = true),
            requestType = JSONRPCRequest::class,
        ) {
            path("/mcp")
            containsHeader("Mcp-Session-Id", sessionId)
        } respondsWith {
            body = null
        }
    }
}
