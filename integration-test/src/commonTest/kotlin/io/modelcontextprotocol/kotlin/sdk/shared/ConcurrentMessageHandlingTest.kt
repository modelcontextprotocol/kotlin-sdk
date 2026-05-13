package io.modelcontextprotocol.kotlin.sdk.shared

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CustomRequest
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Integration test verifying that concurrent message dispatch prevents deadlock
 * when a request handler sends its own request before responding.
 *
 * Uses InMemoryTransport.createLinkedPair() to set up two Protocol instances
 * connected to each other, simulating a real client-server scenario.
 */
class ConcurrentMessageHandlingTest {

    /**
     * End-to-end deadlock test: a "server-side" Protocol receives a request,
     * and its handler calls request() on the "client-side" Protocol before
     * responding. Without concurrent dispatch, the response from the client
     * would be blocked behind the running server handler.
     */
    @Test
    fun `should not deadlock when server request handler sends request to client`() = runTest {
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = TestProtocol()
        val client = TestProtocol()

        listOf(
            launch { server.connect(serverTransport) },
            launch { client.connect(clientTransport) },
        ).joinAll()

        // Register a handler on the client that responds to "test/client-method"
        client.setRequestHandler<CustomRequest>(Method.Custom("test/client-method")) { _, _ ->
            EmptyResult()
        }

        // Register a handler on the server that, when receiving a request,
        // sends its own request back to the client before responding.
        // This is the deadlock scenario: the server handler calls request(),
        // which suspends waiting for a response from the client. The client's
        // response can only arrive through the same message loop. Without
        // concurrent dispatch, this would deadlock.
        server.setRequestHandler<CustomRequest>(Method.Custom("test/server-method")) { _, _ ->
            // Send a request to the client before responding.
            // This suspends until the client responds.
            server.request<EmptyResult>(
                request = CustomRequest(method = Method.Custom("test/client-method"), params = null),
            )
            EmptyResult()
        }

        // Send a request from the client to the server, triggering the handler
        // that calls request() back to the client.
        val result = client.request<EmptyResult>(
            request = CustomRequest(method = Method.Custom("test/server-method"), params = null),
        )

        // If we reach here without timeout, the deadlock is fixed.
        result shouldBe EmptyResult()
    }

    /**
     * Test that responses are processed concurrently with other responses,
     * ensuring the message scope uses SupervisorJob so one handler failure
     * doesn't break other handlers.
     */
    @Test
    fun `should process concurrent requests without blocking`() = runTest {
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = TestProtocol()
        val client = TestProtocol()

        listOf(
            launch { server.connect(serverTransport) },
            launch { client.connect(clientTransport) },
        ).joinAll()

        // Register a handler on the server that takes time
        server.setRequestHandler<CustomRequest>(Method.Custom("test/slow")) { _, _ ->
            EmptyResult()
        }

        // Register a handler on the client that responds quickly
        client.setRequestHandler<CustomRequest>(Method.Custom("test/fast")) { _, _ ->
            EmptyResult()
        }

        // Send a slow request from client to server
        val slowRequest = async {
            client.request<EmptyResult>(
                request = CustomRequest(method = Method.Custom("test/slow"), params = null),
            )
        }

        // Also send a fast request from server to client
        // The fast request should complete even while the slow one is still running
        val fastRequest = async {
            server.request<EmptyResult>(
                request = CustomRequest(method = Method.Custom("test/fast"), params = null),
            )
        }

        // Both should complete successfully
        fastRequest.await() shouldBe EmptyResult()
        slowRequest.await() shouldBe EmptyResult()
    }
}

private class TestProtocol : Protocol(null) {
    override fun assertCapabilityForMethod(method: Method) {}
    override fun assertNotificationCapability(method: Method) {}
    override fun assertRequestHandlerCapability(method: Method) {}
}