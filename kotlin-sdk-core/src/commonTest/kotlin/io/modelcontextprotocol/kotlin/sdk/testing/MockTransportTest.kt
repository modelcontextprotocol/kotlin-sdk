package io.modelcontextprotocol.kotlin.sdk.testing

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MockTransportTest {

    private lateinit var transport: MockTransport

    @BeforeTest
    fun beforeTest() {
        transport = MockTransport {
            // configure mock transport behavior
            onMessageReplyResult(Method.Defined.Initialize) {
                InitializeResult(
                    protocolVersion = "2024-11-05",
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools(listChanged = null),
                    ),
                    serverInfo = Implementation("mock-server", "1.0.0"),
                )
            }
        }

        // Set up onMessage callback to add messages
        transport.onMessage { }
    }

    @Test
    fun `awaitMessage should return message when predicate matches`() = runTest {
        // Trigger the onMessage callback directly via send
        launch {
            transport.send(
                JSONRPCRequest(
                    id = RequestId.StringId("some-id"),
                    method = "initialize",
                ),
            )
            delay(200)
            transport.send(
                JSONRPCRequest(
                    id = RequestId.StringId("test-id"),
                    method = "initialize",
                    params = buildJsonObject {
                        put("foo", JsonPrimitive("bar"))
                    },
                ),
            )
            transport.send(
                JSONRPCRequest(
                    id = RequestId.StringId("other-id"),
                    method = "initialize",
                ),
            )
        }

        // Wait for the auto-response
        val message = transport.awaitMessage {
            it is JSONRPCResponse &&
                it.id == RequestId.StringId("test-id")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("test-id"), message.id)
    }

    @Test
    fun `awaitMessage should timeout when no matching message arrives`() = runTest {
        val exception = assertFailsWith<IllegalStateException> {
            transport.awaitMessage(
                timeout = 100.milliseconds,
                timeoutMessage = "Custom timeout message",
            ) { false } // Predicate that never matches
        }

        assertEquals("Custom timeout message", exception.message)
    }

    @Test
    fun `awaitMessage should filter messages by predicate`() = runTest {
        transport.onMessageReply(predicate = { true }) {
            JSONRPCResponse(
                id = it.id,
                result = EmptyRequestResult(),
            )
        }
        // Send multiple messages
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-1"),
                method = "test1",
                params = buildJsonObject { },
            ),
        )
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-2"),
                method = "test2",
                params = buildJsonObject { },
            ),
        )

        // Wait for response with specific id - note: no auto-response for non-initialize/tools methods
        // So this test will timeout unless we manually trigger a response
        // Let's send an initialize to get a response
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-2"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        val message = transport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("req-2")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("req-2"), message.id)
    }

    @Test
    fun `awaitMessage should return first matching message`() = runTest {
        // Send initialize request to get auto-response
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("init-1"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Wait for any response
        val message = transport.awaitMessage { it is JSONRPCResponse }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("init-1"), message.id)
    }

    @Test
    fun `awaitMessage should handle concurrent access safely`() = runTest {
        // Send a message that will trigger auto-response
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("concurrent-test"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Launch multiple concurrent awaitMessage calls
        val deferred1 = async {
            transport.awaitMessage { it is JSONRPCResponse }
        }

        val deferred2 = async {
            transport.awaitMessage { it is JSONRPCResponse }
        }

        val deferred3 = async {
            transport.awaitMessage { it is JSONRPCResponse }
        }

        // All should successfully find the message
        val message1 = deferred1.await()
        val message2 = deferred2.await()
        val message3 = deferred3.await()

        assertNotNull(message1)
        assertNotNull(message2)
        assertNotNull(message3)

        // All should be the same message
        assertTrue(message1 is JSONRPCResponse)
        assertTrue(message2 is JSONRPCResponse)
        assertTrue(message3 is JSONRPCResponse)
        assertEquals(RequestId.StringId("concurrent-test"), message1.id)
        assertEquals(RequestId.StringId("concurrent-test"), message2.id)
        assertEquals(RequestId.StringId("concurrent-test"), message3.id)
    }

    @Test
    fun `awaitMessage should wait for message to arrive`() = runTest {
        // Launch awaitMessage before message arrives
        val deferred = async {
            transport.awaitMessage(timeout = 2.seconds) { it is JSONRPCResponse }
        }

        // Wait a bit before sending message
        delay(100.milliseconds)

        // Now send the message
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("delayed"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Should successfully receive it
        val message = deferred.await()
        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("delayed"), message.id)
    }

    @Test
    fun `awaitMessage should use custom pool interval`() = runTest {
        // Send message
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("pool-test"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Should work with custom pool interval
        val message = transport.awaitMessage(
            poolInterval = 10.milliseconds,
            timeout = 1.seconds,
        ) { it is JSONRPCResponse }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
    }

    @Test
    fun `awaitMessage should handle tools call auto-response`() = runTest {
        transport.onMessageReplyResult(Method.Defined.ToolsCall) {
            CallToolResult(content = listOf())
        }

        // Send tools/call request
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("tool-1"),
                method = "tools/call",
                params = buildJsonObject { },
            ),
        )

        // Should receive auto-response
        val message = transport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("tool-1")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("tool-1"), message.id)
    }

    @Test
    fun `awaitMessage should return existing message immediately`() = runTest {
        // Send message first
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("existing"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Give it time to be received
        delay(50.milliseconds)

        // Now await should return immediately without waiting
        val message = transport.awaitMessage(
            timeout = 100.milliseconds,
        ) { it is JSONRPCResponse }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("existing"), message.id)
    }

    @Test
    fun `awaitMessage with complex predicate`() = runTest {
        transport.onMessageReply(predicate = { true }) {
            JSONRPCResponse(
                id = it.id,
                result = EmptyRequestResult(),
            )
        }
        // Send multiple requests
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-1"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )
        transport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-2"),
                method = "tools/call",
                params = buildJsonObject { },
            ),
        )

        // Wait for response with specific criteria
        val message = transport.awaitMessage { msg ->
            msg is JSONRPCResponse && msg.id == RequestId.StringId("req-2")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("req-2"), message.id)
    }

    @Test
    fun `onMessageReply should register handler with custom predicate`() = runTest {
        val customTransport = MockTransport()
        customTransport.onMessage { }

        // Register handler that only responds to requests with "custom" method
        customTransport.onMessageReply(
            predicate = { request -> request.method == "custom-method" },
        ) { request ->
            JSONRPCResponse(
                id = request.id,
                result = EmptyRequestResult(),
            )
        }

        // Send matching request
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("test-1"),
                method = "custom-method",
                params = buildJsonObject { },
            ),
        )

        // Verify response was received
        val message = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("test-1")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("test-1"), message.id)
        assertNotNull(message.result)
    }

    @Test
    fun `onMessageReply should support multiple handlers with different predicates`() = runTest {
        val customTransport = MockTransport()
        customTransport.onMessage { }

        // Register first handler for "method-a"
        customTransport.onMessageReply(
            predicate = { it.method == "method-a" },
        ) { request ->
            JSONRPCResponse(
                id = request.id,
                result = CallToolResult(content = listOf()),
            )
        }

        // Register second handler for "method-b"
        customTransport.onMessageReply(
            predicate = { it.method == "method-b" },
        ) { request ->
            JSONRPCResponse(
                id = request.id,
                result = EmptyRequestResult(),
            )
        }

        // Test first handler
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-a"),
                method = "method-a",
                params = buildJsonObject { },
            ),
        )

        val messageA = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("req-a")
        }

        assertTrue(messageA is JSONRPCResponse)
        assertTrue(messageA.result is CallToolResult)

        // Test second handler
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("req-b"),
                method = "method-b",
                params = buildJsonObject { },
            ),
        )

        val messageB = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("req-b")
        }

        assertTrue(messageB is JSONRPCResponse)
        assertTrue(messageB.result is EmptyRequestResult)
    }

    @Test
    fun `onMessageReplyResult should create response with result for matching method`() = runTest {
        val customTransport = MockTransport()
        customTransport.onMessage { }

        // Register handler using onMessageReplyResult
        customTransport.onMessageReplyResult(Method.Defined.Initialize) { _ ->
            InitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = null),
                ),
                serverInfo = Implementation("test-server", "1.0.0"),
            )
        }

        // Send matching request
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("init-test"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Verify response with result
        val message = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("init-test")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("init-test"), message.id)
        assertNotNull(message.result)
        assertTrue(message.result is InitializeResult)
        val result = message.result
        assertEquals("2024-11-05", result.protocolVersion)
        assertEquals("test-server", result.serverInfo.name)
    }

    @Test
    fun `onMessageReplyResult should only respond to specified method`() = runTest {
        val customTransport = MockTransport()
        customTransport.onMessage { }

        // Register handler only for Initialize
        customTransport.onMessageReplyResult(Method.Defined.Initialize) {
            InitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = null),
                ),
                serverInfo = Implementation("test", "1.0"),
            )
        }

        // Also register a catch-all handler for other methods
        customTransport.onMessageReply(predicate = { it.method != "initialize" }) {
            JSONRPCResponse(
                id = it.id,
                result = EmptyRequestResult(),
            )
        }

        // Send non-matching request
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("other-method"),
                method = "other-method",
                params = buildJsonObject { },
            ),
        )

        // Should get response from catch-all handler
        val message = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("other-method")
        }

        assertTrue(message is JSONRPCResponse)
        assertTrue(message.result is EmptyRequestResult)
    }

    @Test
    fun `onMessageReplyError should create response with error for matching method`() = runTest {
        val customTransport = MockTransport()
        customTransport.onMessage { }

        // Register error handler with custom error
        customTransport.onMessageReplyError(Method.Defined.ToolsCall) { _ ->
            io.modelcontextprotocol.kotlin.sdk.JSONRPCError(
                code = io.modelcontextprotocol.kotlin.sdk.ErrorCode.Defined.InvalidParams,
                message = "Custom error message",
            )
        }

        // Send matching request
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("error-test"),
                method = "tools/call",
                params = buildJsonObject { },
            ),
        )

        // Verify response with error
        val message = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("error-test")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("error-test"), message.id)
        assertNotNull(message.error)
        assertEquals(io.modelcontextprotocol.kotlin.sdk.ErrorCode.Defined.InvalidParams, message.error?.code)
        assertEquals("Custom error message", message.error.message)
    }

    @Test
    fun `onMessageReplyError should use default error when block not provided`() = runTest {
        val customTransport = MockTransport()
        customTransport.onMessage { }

        // Register error handler without custom block (using default)
        customTransport.onMessageReplyError(Method.Defined.Initialize)

        // Send matching request
        customTransport.send(
            JSONRPCRequest(
                id = RequestId.StringId("default-error-test"),
                method = "initialize",
                params = buildJsonObject { },
            ),
        )

        // Verify response with default error
        val message = customTransport.awaitMessage {
            it is JSONRPCResponse && it.id == RequestId.StringId("default-error-test")
        }

        assertNotNull(message)
        assertTrue(message is JSONRPCResponse)
        assertEquals(RequestId.StringId("default-error-test"), message.id)
        assertNotNull(message.error)
        assertEquals(io.modelcontextprotocol.kotlin.sdk.ErrorCode.Defined.InternalError, message.error?.code)
        assertEquals("Expected error", message.error.message)
    }
}
