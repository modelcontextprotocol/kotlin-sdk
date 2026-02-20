package io.modelcontextprotocol.kotlin.sdk.client.streamable.http

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StreamableHttpClientTransportTest {

    private fun createTransport(handler: MockRequestHandler): StreamableHttpClientTransport {
        val mockEngine = MockEngine(handler)
        val httpClient = HttpClient(mockEngine) {
            install(SSE) {
                reconnectionTime = 1.seconds
            }
        }

        return StreamableHttpClientTransport(httpClient, url = "http://localhost:8080/mcp")
    }

    private fun buildSseMessage(
        id: String,
        method: String,
        params: String,
    ): String = buildString {
        appendLine("event: message")
        appendLine("id: $id")
        val data = """{"jsonrpc":"2.0","method":"$method","params":$params}"""
        appendLine("data: $data")
        appendLine()
    }

    @Test
    fun testSendJsonRpcMessage() = runTest {
        val message = JSONRPCRequest(
            id = "test-id",
            method = "test",
            params = buildJsonObject { },
        )

        val transport = createTransport { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://localhost:8080/mcp", request.url.toString())
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val body = (request.body as TextContent).text
            val decodedMessage = McpJson.decodeFromString<JSONRPCMessage>(body)
            assertEquals(message, decodedMessage)

            respond(
                content = "",
                status = HttpStatusCode.Accepted,
            )
        }

        transport.start()
        transport.send(message)
        transport.close()
    }

    @Test
    fun testStartTransportTwoTimesThrowsException() = runTest {
        val transport = createTransport { _ ->
            respond(
                content = "",
                status = HttpStatusCode.Accepted,
            )
        }

        transport.start()
        try {
            transport.start()
        } catch (e: IllegalStateException) {
            e shouldBe IllegalStateException("Can't change state: expected transport state New, but found Operational.")
        }
        transport.close()
    }

    @Test
    fun testCloseTransportTwoTimesIsPossible() = runTest {
        val transport = createTransport { _ ->
            respond(
                content = "",
                status = HttpStatusCode.Accepted,
            )
        }

        transport.start()
        transport.close()
        try {
            transport.close()
        } catch (e: Exception) {
            withClue("We expect no exceptions when closing already closed transport") { e shouldBe null }
        }
    }

    @Test
    fun testStoreSessionId() = runTest {
        val initMessage = JSONRPCRequest(
            id = "test-id",
            method = "initialize",
            params = buildJsonObject {
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", JsonPrimitive("test-client"))
                        put("version", JsonPrimitive("1.0"))
                    },
                )
                put("protocolVersion", JsonPrimitive("2025-06-18"))
            },
        )

        val transport = createTransport { request ->
            when (
                val msg = McpJson.decodeFromString<JSONRPCMessage>(
                    (request.body as TextContent).text,
                )
            ) {
                is JSONRPCRequest if msg.method == "initialize" -> respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf("mcp-session-id", "test-session-id"),
                )

                is JSONRPCNotification if msg.method == "test" -> {
                    assertEquals("test-session-id", request.headers["mcp-session-id"])
                    respond(
                        content = "",
                        status = HttpStatusCode.Accepted,
                    )
                }

                else -> error("Unexpected message: $msg")
            }
        }

        transport.start()
        transport.send(initMessage)

        assertEquals("test-session-id", transport.sessionId)

        transport.send(JSONRPCNotification(method = "test"))

        transport.close()
    }

    @Test
    fun testTerminateSession() = runTest {
//        transport.sessionId = "test-session-id"

        val transport = createTransport { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("test-session-id", request.headers["mcp-session-id"])
            respond(
                content = "",
                status = HttpStatusCode.OK,
            )
        }

        transport.start()
        transport.terminateSession()

        assertNull(transport.sessionId)
        transport.close()
    }

    @Test
    fun testTerminateSessionHandle405() = runTest {
//        transport.sessionId = "test-session-id"

        val transport = createTransport { request ->
            assertEquals(HttpMethod.Delete, request.method)
            respond(
                content = "",
                status = HttpStatusCode.MethodNotAllowed,
            )
        }

        transport.start()
        // Should not throw for 405
        transport.terminateSession()

        // Session ID should still be cleared
        assertNull(transport.sessionId)
        transport.close()
    }

    @Test
    fun testProtocolVersionHeader() = runTest {
        val transport = createTransport { request ->
            assertEquals("2025-06-18", request.headers["mcp-protocol-version"])
            respond(
                content = "",
                status = HttpStatusCode.Accepted,
            )
        }
        transport.protocolVersion = "2025-06-18"

        transport.start()
        transport.send(JSONRPCNotification(method = "test"))
        transport.close()
    }

    // Engine doesn't support SSECapability: https://youtrack.jetbrains.com/issue/KTOR-8177/MockEngine-Add-SSE-support
    @Ignore
    @Test
    fun testNotificationSchemaE2E() = runTest {
        val receivedMessages = mutableListOf<JSONRPCMessage>()
        var sseStarted = false

        val transport = createTransport { request ->
            when (request.method) {
                HttpMethod.Post if request.body.toString().contains("notifications/initialized") -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.Accepted,
                        headers = headersOf("mcp-session-id", "notification-test-session"),
                    )
                }

                // Handle SSE connection
                HttpMethod.Get -> {
                    sseStarted = true
                    val sseContent = buildString {
                        // Server sends various notifications
                        appendLine("event: message")
                        appendLine("id: 1")
                        appendLine(
                            """data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"upload-123","progress":50,"total":100}}""",
                        )
                        appendLine()

                        appendLine("event: message")
                        appendLine("id: 2")
                        appendLine("""data: {"jsonrpc":"2.0","method":"notifications/resources/list_changed"}""")
                        appendLine()

                        appendLine("event: message")
                        appendLine("id: 3")
                        appendLine("""data: {"jsonrpc":"2.0","method":"notifications/tools/list_changed"}""")
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
                }

                // Handle regular notifications
                HttpMethod.Post -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.Accepted,
                    )
                }

                else -> respond("", HttpStatusCode.OK)
            }
        }

        transport.onMessage { message ->
            receivedMessages.add(message)
        }

        transport.start()

        // Test 1: Send initialized notification to trigger SSE
        val initializedNotification = JSONRPCNotification(
            method = "notifications/initialized",
            params = buildJsonObject {
                put("protocolVersion", JsonPrimitive("1.0"))
                put(
                    "capabilities",
                    buildJsonObject {
                        put("tools", JsonPrimitive(true))
                        put("resources", JsonPrimitive(true))
                    },
                )
            },
        )

        transport.send(initializedNotification)

        // Verify SSE was triggered
        assertTrue(sseStarted, "SSE should start after initialized notification")

        // Test 2: Verify received notifications
        assertEquals(3, receivedMessages.size)
        assertTrue(receivedMessages.all { it is JSONRPCNotification })

        val notifications = receivedMessages.filterIsInstance<JSONRPCNotification>()

        // Verify progress notification
        val progressNotif = notifications[0]
        assertEquals("notifications/progress", progressNotif.method)
        val progressParams = progressNotif.params as JsonObject
        assertEquals("upload-123", (progressParams["progressToken"] as JsonPrimitive).content)
        assertEquals(50, (progressParams["progress"] as JsonPrimitive).content.toInt())

        // Verify list changed notifications
        assertEquals("notifications/resources/list_changed", notifications[1].method)
        assertEquals("notifications/tools/list_changed", notifications[2].method)

        // Test 3: Send various client notifications
        val clientNotifications = listOf(
            JSONRPCNotification(
                method = "notifications/progress",
                params = buildJsonObject {
                    put("progressToken", JsonPrimitive("download-456"))
                    put("progress", JsonPrimitive(75))
                },
            ),
            JSONRPCNotification(
                method = "notifications/cancelled",
                params = buildJsonObject {
                    put("requestId", JsonPrimitive("req-789"))
                    put("reason", JsonPrimitive("user_cancelled"))
                },
            ),
            JSONRPCNotification(
                method = "notifications/message",
                params = buildJsonObject {
                    put("level", JsonPrimitive("info"))
                    put("message", JsonPrimitive("Operation completed"))
                    put(
                        "data",
                        buildJsonObject {
                            put("duration", JsonPrimitive(1234))
                        },
                    )
                },
            ),
        )

        // Send all client notifications
        clientNotifications.forEach { notification ->
            transport.send(notification)
        }

        // Verify session ID is maintained
        assertEquals("notification-test-session", transport.sessionId)
        transport.close()
    }

    // Engine doesn't support SSECapability: https://youtrack.jetbrains.com/issue/KTOR-8177/MockEngine-Add-SSE-support
    @Ignore
    @Test
    fun testNotificationWithResumptionToken() = runTest {
        var resumptionTokenReceived: String? = null
        var lastEventIdSent: String? = null

        val transport = createTransport { request ->
            // Capture Last-Event-ID header
            lastEventIdSent = request.headers["Last-Event-ID"]

            when (request.method) {
                HttpMethod.Get -> {
                    val sseContent = buildString {
                        appendLine("event: message")
                        appendLine("id: resume-100")
                        appendLine(
                            """data: {"jsonrpc":"2.0","method":"notifications/resumed","params":{"fromToken":"$lastEventIdSent"}}""",
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
                }

                else -> respond("", HttpStatusCode.Accepted)
            }
        }

        transport.start()

        // Send notification with resumption token
        transport.send(
            message = JSONRPCNotification(
                method = "notifications/test",
                params = buildJsonObject {
                    put("data", JsonPrimitive("test-data"))
                },
            ),
            resumptionToken = "previous-token-99",
            onResumptionToken = { token ->
                resumptionTokenReceived = token
            },
        )

        // Wait for response
        delay(1.seconds)

        // Verify resumption token was sent in header
        assertEquals("previous-token-99", lastEventIdSent)

        // Verify new resumption token was received
        assertEquals("resume-100", resumptionTokenReceived)
        transport.close()
    }

    @Test
    fun testClientConnectWithInvalidJson() = runTest {
        // Transport under test: respond with invalid JSON for the initialize request
        val transport = createTransport { _ ->
            respond(
                "this is not valid json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test-client",
                version = "1.0",
            ),
        )

        try {
            // Real time-keeping is needed; otherwise Protocol will always throw TimeoutCancellationException in tests
            val mcpException = assertFailsWith<McpException>(
                message = "Expected client.connect to fail on invalid JSON response",
            ) {
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5.seconds) {
                        client.connect(transport)
                    }
                }
            }
            mcpException.code shouldBe RPCError.ErrorCode.INTERNAL_ERROR
        } finally {
            transport.close()
        }
    }

    @Test
    fun testInlineSSEInResponse() = runTest {
        val transport = createTransport { request ->
            if (request.method == HttpMethod.Post) {
                val sseContent = buildString {
                    append(
                        buildSseMessage(
                            id = "1",
                            method = "notifications/progress",
                            params = """{"progressToken":"task-1","progress":50}""",
                        )
                    )
                    append(
                        buildSseMessage(
                            id = "2",
                            method = "notifications/tools/list_changed",
                            params = "{}",
                        )
                    )
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

        val receivedMessages = mutableListOf<JSONRPCMessage>()
        val twoMessagesReceived = CompletableDeferred<Unit>()

        transport.onMessage { message ->
            receivedMessages.add(message)
            if (receivedMessages.size >= 2 && !twoMessagesReceived.isCompleted) {
                twoMessagesReceived.complete(Unit)
            }
        }

        transport.start()

        transport.send(
            JSONRPCRequest(
                id = "test-1",
                method = "test",
                params = buildJsonObject { },
            ),
        )

        withTimeout(2.seconds) {
            twoMessagesReceived.await()
        }

        receivedMessages.size shouldBe 2

        val firstNotification = receivedMessages[0] as JSONRPCNotification
        firstNotification.method shouldBe "notifications/progress"

        val secondNotification = receivedMessages[1] as JSONRPCNotification
        secondNotification.method shouldBe "notifications/tools/list_changed"

        transport.close()
    }

    @Test
    fun testErrorInSSEResponse() = runTest {
        val errorMessage = "Something went wrong on the server"
        val transport = createTransport { request ->
            if (request.method == HttpMethod.Post) {
                val sseContent = buildString {
                    appendLine("event: error")
                    appendLine("data: $errorMessage")
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

        val receivedErrors = mutableListOf<Throwable>()
        val errorDeferred = CompletableDeferred<Throwable>()

        transport.onError { error ->
            receivedErrors.add(error)
            if (!errorDeferred.isCompleted) {
                errorDeferred.complete(error)
            }
        }

        transport.start()

        transport.send(
            JSONRPCRequest(
                id = "test-error",
                method = "test",
                params = buildJsonObject { },
            ),
        )

        val error = withTimeout(2.seconds) { errorDeferred.await() }

        receivedErrors.size shouldBe 1
        error.message shouldBe "Streamable HTTP error: $errorMessage"

        transport.close()
    }

    @Test
    fun testCloseTransportDuringEventStream() = runTest {
        val transport = createTransport { request ->
            if (request.method == HttpMethod.Post) {
                val sseContent = buildString {
                    append(
                        buildSseMessage(
                            id = "1",
                            method = "notifications/progress",
                            params = """{"progressToken":"task-1","progress":25}""",
                        )
                    )
                    append(
                        buildSseMessage(
                            id = "2",
                            method = "notifications/progress",
                            params = """{"progressToken":"task-1","progress":50}""",
                        )
                    )
                    append(
                        buildSseMessage(
                            id = "3",
                            method = "notifications/progress",
                            params = """{"progressToken":"task-1","progress":75}""",
                        )
                    )
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

        val (receivedMessages, receivedErrors) = setupTransportAndCollectMessages(transport)

        withTimeout(2.seconds) {
            while (receivedMessages.isEmpty()) {
                delay(10)
            }
        }

        transport.close()

        assertTrue(receivedMessages.isNotEmpty())

        receivedMessages.forEach { message ->
            message.shouldBeInstanceOf<JSONRPCNotification>()
            message.method shouldBe "notifications/progress"
        }
        receivedErrors shouldHaveSize 0
    }

    private suspend fun setupTransportAndCollectMessages(
        transport: StreamableHttpClientTransport,
    ): Pair<MutableList<JSONRPCMessage>, MutableList<Throwable>> {
        val receivedMessages = mutableListOf<JSONRPCMessage>()
        val receivedErrors = mutableListOf<Throwable>()

        transport.onMessage { message ->
            receivedMessages.add(message)
        }

        transport.onError { error ->
            receivedErrors.add(error)
        }

        transport.start()

        transport.send(
            JSONRPCRequest(
                id = "test-close",
                method = "test",
                params = buildJsonObject { },
            ),
        )

        return receivedMessages to receivedErrors
    }
}
