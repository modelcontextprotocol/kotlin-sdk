package io.modelcontextprotocol.kotlin.sdk.client.streamable.http

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskPayloadRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskPayloadRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetTaskPayloadResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Regression test for https://github.com/modelcontextprotocol/kotlin-sdk/issues/601 on the
 * Streamable HTTP SSE replay path.
 *
 * Lives in `jvmTest` (not `commonTest`) because it drives a full `Protocol.request` round trip
 * over a MockEngine: under `runTest`'s virtual clock the request's internal timeout fires while
 * the real-dispatcher HTTP delivery is still in flight, so the test needs `runBlocking`.
 */
class StreamableHttpClientTransportReplayTest {

    @Test
    fun `should deserialize a replayed SSE response by the original request method`() = runBlocking {
        // When a response arrives on the SSE stream with a different id, the transport rewrites it
        // to the POSTed request's id. The rewrite must preserve the raw result JSON so that
        // Protocol can decode the result according to the original request's method instead of
        // guessing from the JSON shape.
        val protocol = object : Protocol(null) {
            override fun assertCapabilityForMethod(method: Method) = Unit
            override fun assertNotificationCapability(method: Method) = Unit
            override fun assertRequestHandlerCapability(method: Method) = Unit
        }

        val mockEngine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                val sseContent = buildString {
                    appendLine("id: ev-1")
                    appendLine("event: message")
                    appendLine(
                        """data: {"jsonrpc":"2.0","id":"server-side-id",""" +
                            """"result":{"content":[{"type":"text","text":"task output"}],"isError":false}}""",
                    )
                    appendLine()
                }

                respond(
                    content = ByteReadChannel(sseContent),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Text.EventStream.toString(),
                    ),
                )
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        val httpClient = HttpClient(mockEngine) {
            install(SSE) {
                reconnectionTime = 1.seconds
            }
        }
        val transport = StreamableHttpClientTransport(httpClient, url = "http://localhost:8080/mcp")

        protocol.connect(transport)

        val result = protocol.request<GetTaskPayloadResult>(
            GetTaskPayloadRequest(GetTaskPayloadRequestParams(taskId = "task-42")),
        )

        // The tasks/result payload is CallToolResult-shaped; it must still surface as
        // GetTaskPayloadResult with the raw payload preserved.
        result["content"].shouldNotBeNull()
        result["isError"]?.jsonPrimitive?.boolean shouldBe false

        transport.close()
    }
}
