package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ProtocolNotificationHandlingTest {
    private lateinit var protocol: TestProtocol
    private lateinit var transport: TestTransport

    @BeforeTest
    fun setUp() {
        protocol = TestProtocol()
        transport = TestTransport()

        runBlocking {
            protocol.connect(transport)
        }
    }

    @Test
    fun `onNotification adds method key to JsonObject params when missing`() = runTest {
        val originalParams = buildJsonObject { put("data", 123) }
        val notification = JSONRPCNotification(
            method = "test/notification",
            params = originalParams
        )

        transport.simulateMessage(notification)

        assertNotNull(protocol.receivedNotification, "Handler should have captured the notification")
        val receivedParams = protocol.receivedNotification?.params
        assertIs<JsonObject>(receivedParams, "Params should be JsonObject")

        assertEquals(
            buildJsonObject {
                put("data", 123)
                put("method", "test/notification")
            },
            receivedParams
        )
        assertFalse(protocol.errorHandlerCalled, "onError should not be called")
    }

    @Test
    fun `onNotification should not modify params if JsonObject and method exists`() = runTest {
        val originalParams = buildJsonObject {
            put("data", 123)
            put("method", "test/notification")
        }
        val notification = JSONRPCNotification(
            method = "test/notification",
            params = originalParams
        )

        transport.simulateMessage(notification)

        assertNotNull(protocol.receivedNotification, "Handler should have captured the notification")
        val receivedParams = protocol.receivedNotification?.params
        assertIs<JsonObject>(receivedParams, "Params should be JsonObject")

        // Because "method" already exists, it should be unchanged
        assertEquals(originalParams, receivedParams)
        assertFalse(protocol.errorHandlerCalled, "onError should not be called")
    }

    @Test
    fun `onNotification should not modify params if JsonArray`() = runTest {
        val originalParams = buildJsonArray { add(1); add("test") }
        val notification = JSONRPCNotification(
            method = "test/notification",
            params = originalParams
        )

        transport.simulateMessage(notification)

        assertNotNull(protocol.receivedNotification, "Handler should have captured the notification")
        val receivedParams = protocol.receivedNotification?.params
        assertIs<JsonArray>(receivedParams, "Params should be JsonArray")
        // Should remain unmodified
        assertEquals(originalParams, receivedParams)
        assertFalse(protocol.errorHandlerCalled, "onError should not be called")
    }

    @Test
    fun `onNotification should handle JsonNull params`() = runTest {
        val notification = JSONRPCNotification(
            method = "test/notification",
            params = JsonNull
        )

        transport.simulateMessage(notification)

        assertNotNull(protocol.receivedNotification, "Handler should have captured the notification")

        // Should remain JsonNull
        assertEquals(JsonNull, protocol.receivedNotification?.params)
        assertFalse(protocol.errorHandlerCalled, "onError should not be called")
    }

    @Test
    fun `onNotification should call fallback handler if specific handler not found`() = runTest {
        val notification = JSONRPCNotification(
            method = "unregistered/notification",
            params = buildJsonObject { put("value", true) }
        )

        transport.simulateMessage(notification)

        assertNotNull(protocol.receivedNotification, "Fallback handler should have captured the notification")
        assertEquals("unregistered/notification", protocol.receivedNotification?.method)
        val receivedParams = protocol.receivedNotification?.params
        assertIs<JsonObject>(receivedParams)

        // Because we had no specific handler, "method" gets auto-added
        assertEquals(
            buildJsonObject {
                put("value", true)
                put("method", "unregistered/notification")
            },
            receivedParams
        )
        assertFalse(protocol.errorHandlerCalled, "onError should not be called")
    }

    @Test
    fun `onNotification should call onError if handler throws exception`() = runTest {
        val exception = RuntimeException("Handler error!")
        protocol.notificationHandlers["error/notification"] = {
            throw exception
        }

        val notification = JSONRPCNotification(
            method = "error/notification",
            params = buildJsonObject { put("method", "error/notification") }
        )

        transport.simulateMessage(notification)

        // Because the handler throws, the protocol's onError callback should run
        assertNull(protocol.receivedNotification, "Received notification should be null since handler threw")
        assertTrue(protocol.errorHandlerCalled, "onError should have been called")
        assertSame(exception, protocol.lastError, "onError should receive the correct exception")
    }
}
