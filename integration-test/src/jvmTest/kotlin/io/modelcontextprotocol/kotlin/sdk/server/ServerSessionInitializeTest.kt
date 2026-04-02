package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ServerSessionInitializeTest {

    private fun createSession(): ServerSession = ServerSession(
        serverInfo = Implementation(name = "test-server", version = "1.0"),
        options = ServerOptions(capabilities = ServerCapabilities()),
        instructions = null,
    )

    private fun createInitializeRequest(clientName: String = "test-client"): InitializeRequest = InitializeRequest(
        InitializeRequestParams(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = clientName, version = "1.0"),
        ),
    )

    @Test
    fun `should handle first initialize request successfully`() = runTest {
        val session = createSession()
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        assertNull(session.clientCapabilities)
        assertNull(session.clientVersion)

        val responseDone = CompletableDeferred<JSONRPCResponse>()
        clientTransport.onMessage { message ->
            if (message is JSONRPCResponse) {
                responseDone.complete(message)
            }
        }

        session.connect(serverTransport)
        clientTransport.send(createInitializeRequest().toJSON())

        val response = responseDone.await()
        assertNotNull(response.result)
        assertNotNull(session.clientCapabilities)
        assertEquals("test-client", session.clientVersion?.name)
    }

    @Test
    fun `should reject duplicate initialize request`() = runTest {
        val session = createSession()
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val responses = CopyOnWriteArrayList<JSONRPCMessage>()
        val secondResponseDone = CompletableDeferred<Unit>()

        clientTransport.onMessage { message ->
            when (message) {
                is JSONRPCResponse, is JSONRPCError -> {
                    responses.add(message)
                    if (responses.size == 2) secondResponseDone.complete(Unit)
                }

                else -> {}
            }
        }

        session.connect(serverTransport)

        // First initialize should succeed
        clientTransport.send(createInitializeRequest(clientName = "first-client").toJSON())

        // Second initialize should be rejected
        clientTransport.send(createInitializeRequest(clientName = "second-client").toJSON())

        secondResponseDone.await()

        responses.size shouldBe 2
        // With concurrent dispatch, responses may arrive in any order
        val successResponses = responses.filterIsInstance<JSONRPCResponse>()
        val errorResponses = responses.filterIsInstance<JSONRPCError>()
        successResponses.shouldHaveSize(1)
        errorResponses.shouldHaveSize(1)
        errorResponses[0].error.code shouldBe RPCError.ErrorCode.INVALID_REQUEST

        // Capabilities still reflect the first client, not overwritten
        session.clientVersion?.name shouldBe "first-client"
    }

    @Test
    fun `should reject concurrent initialize requests - only first succeeds`() = runTest {
        val session = createSession()
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val n = 10
        val allResponsesDone = CompletableDeferred<Unit>()
        val successes = CopyOnWriteArrayList<JSONRPCResponse>()
        val errors = CopyOnWriteArrayList<JSONRPCError>()

        clientTransport.onMessage { message ->
            when (message) {
                is JSONRPCResponse -> successes.add(message)
                is JSONRPCError -> errors.add(message)
                else -> {}
            }
            if (successes.size + errors.size == n) {
                allResponsesDone.complete(Unit)
            }
        }

        session.connect(serverTransport)

        // Use Dispatchers.Default for true parallelism on JVM
        withContext(Dispatchers.Default) {
            val barrier = CompletableDeferred<Unit>()
            val jobs = (1..n).map { i ->
                launch {
                    barrier.await()
                    clientTransport.send(
                        createInitializeRequest(clientName = "client-$i").toJSON(),
                    )
                }
            }
            barrier.complete(Unit)
            jobs.joinAll()
        }

        allResponsesDone.await()

        assertEquals(1, successes.size, "Exactly one initialize should succeed")
        assertEquals(n - 1, errors.size, "All other initializes should be rejected")
        errors.forEach { error ->
            assertEquals(RPCError.ErrorCode.INVALID_REQUEST, error.error.code)
        }
    }
}
