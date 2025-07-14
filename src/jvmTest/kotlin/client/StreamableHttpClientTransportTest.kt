package client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class StreamableHttpClientTransportTest {
    private lateinit var mockEngine: MockEngine
    private lateinit var httpClient: HttpClient
    private lateinit var transport: StreamableHttpClientTransport

    @BeforeTest
    fun setup() {
        mockEngine = MockEngine {
            respond(
                ByteReadChannel(""),
                status = HttpStatusCode.OK,
            )
        }

        httpClient = HttpClient(mockEngine) {
            install(SSE) {
                reconnectionTime = 1.seconds
            }
        }

        transport = StreamableHttpClientTransport(httpClient, url = "http://localhost:8080/mcp")
    }

    @AfterTest
    fun teardown() {
        httpClient.close()
    }

    @Test
    fun testSendJsonRpcMessage() = runTest {
        val message = JSONRPCRequest(
            id = RequestId.StringId("test-id"),
            method = "test",
            params = buildJsonObject { }
        )

        mockEngine.config.addHandler { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://localhost:8080/mcp", request.url.toString())
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val body = (request.body as TextContent).text
            val decodedMessage = McpJson.decodeFromString<JSONRPCMessage>(body)
            assertEquals(message, decodedMessage)

            respond(
                content = "",
                status = HttpStatusCode.Accepted
            )
        }

        transport.start()
        transport.send(message)
    }

//    @Test
//    fun testStoreSessionId() = runTest {
//        val initMessage = JSONRPCRequest(
//            id = RequestId.StringId("test-id"),
//            method = "initialize",
//            params = buildJsonObject {
//                put("clientInfo", buildJsonObject {
//                    put("name", JsonPrimitive("test-client"))
//                    put("version", JsonPrimitive("1.0"))
//                })
//                put("protocolVersion", JsonPrimitive("2025-06-18"))
//            }
//        )
//
//        mockEngine.config.addHandler { request ->
//            respond(
//                content = "", status = HttpStatusCode.OK,
//                headers = headersOf("mcp-session-id", "test-session-id")
//            )
//        }
//
//        transport.start()
//        transport.send(initMessage)
//
//        assertEquals("test-session-id", transport.sessionId)
//
//        // Send another message and verify session ID is included
//        mockEngine.config.addHandler { request ->
//            assertEquals("test-session-id", request.headers["mcp-session-id"])
//            respond(
//                content = "",
//                status = HttpStatusCode.Accepted
//            )
//        }
//
//        transport.send(JSONRPCNotification(method = "test"))
//    }

    @Test
    fun testTerminateSession() = runTest {
//        transport.sessionId = "test-session-id"

        mockEngine.config.addHandler { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("test-session-id", request.headers["mcp-session-id"])
            respond(
                content = "",
                status = HttpStatusCode.OK
            )
        }

        transport.start()
        transport.terminateSession()

        assertNull(transport.sessionId)
    }

    @Test
    fun testTerminateSessionHandle405() = runTest {
//        transport.sessionId = "test-session-id"

        mockEngine.config.addHandler { request ->
            assertEquals(HttpMethod.Delete, request.method)
            respond(
                content = "",
                status = HttpStatusCode.MethodNotAllowed
            )
        }

        transport.start()
        // Should not throw for 405
        assertDoesNotThrow {
            transport.terminateSession()
        }

        // Session ID should still be cleared
        assertNull(transport.sessionId)
    }

    @Test
    fun testProtocolVersionHeader() = runTest {
        transport.protocolVersion = "2025-06-18"

        mockEngine.config.addHandler { request ->
            assertEquals("2025-06-18", request.headers["mcp-protocol-version"])
            respond(
                content = "",
                status = HttpStatusCode.Accepted
            )
        }

        transport.start()
        transport.send(JSONRPCNotification(method = "test"))
    }

    @Test
    fun testHandle405ForSSE() = runTest {
        mockEngine.config.addHandler { request ->
            if (request.method == HttpMethod.Get) {
                respond(
                    content = "",
                    status = HttpStatusCode.MethodNotAllowed
                )
            } else {
                respond(
                    content = "",
                    status = HttpStatusCode.Accepted
                )
            }
        }

        transport.start()

        // Start SSE session - should handle 405 gracefully
        val initNotification = JSONRPCNotification(
            method = "notifications/initialized",
        )

        // Should not throw
        assertDoesNotThrow {
            transport.send(initNotification)
        }

        // Transport should still work after 405
        transport.send(JSONRPCNotification(method = "test"))
    }
}
