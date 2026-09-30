package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class ServerSessionInitializeTest {

    private fun createSession(): ServerSession = ServerSession(
        serverInfo = Implementation(name = "test-server", version = "1.0"),
        options = ServerOptions(capabilities = ServerCapabilities()),
        instructions = null,
    )

    private fun createInitializeRequest(clientName: String): InitializeRequest = InitializeRequest(
        InitializeRequestParams(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = ClientCapabilities(),
            clientInfo = Implementation(name = clientName, version = "1.0"),
        ),
    )

    private fun createMalformedInitializeRequest(): JSONRPCRequest = JSONRPCRequest(
        id = RequestId(1),
        method = "initialize",
        params = buildJsonObject {
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "repro")
                put("version", "0.1.0")
            }
        },
    )

    @Test
    fun `should classify malformed initialize params as invalid params`() = runTest {
        val session = createSession()
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val responseDone = CompletableDeferred<JSONRPCError>()
        clientTransport.onMessage { message ->
            if (message is JSONRPCError) {
                responseDone.complete(message)
            }
        }

        session.connect(serverTransport)
        clientTransport.send(createMalformedInitializeRequest())

        val response = responseDone.await()
        response.error.code shouldBe RPCError.ErrorCode.INVALID_PARAMS
        response.error.message shouldNotContain "kotlinx.serialization"
        session.clientCapabilities.shouldBeNull()
        session.clientVersion.shouldBeNull()
    }

    // Both initialize requests arrive before notifications/initialized, in the serial dispatch
    // phase, so processing and response order stay deterministic.
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

        responses shouldHaveSize 2
        responses[0].shouldBeInstanceOf<JSONRPCResponse>()
        responses[1].shouldBeInstanceOf<JSONRPCError>().error.code shouldBe RPCError.ErrorCode.INVALID_REQUEST
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

        successes shouldHaveSize 1
        errors.map { it.error.code } shouldBe List(n - 1) { RPCError.ErrorCode.INVALID_REQUEST }
    }
}
