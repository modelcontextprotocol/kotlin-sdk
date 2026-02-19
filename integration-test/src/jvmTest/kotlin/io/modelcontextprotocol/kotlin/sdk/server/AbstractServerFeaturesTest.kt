package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach

abstract class AbstractServerFeaturesTest {

    protected lateinit var server: Server
    protected lateinit var client: Client

    abstract fun getServerCapabilities(): ServerCapabilities

    protected open fun getClientCapabilities(): ClientCapabilities = ClientCapabilities()

    protected open fun getServerInstructionsProvider(): (() -> String)? = null

    @BeforeEach
    fun setUp() {
        val serverOptions = ServerOptions(
            capabilities = getServerCapabilities(),
        )

        server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = serverOptions,
            instructionsProvider = getServerInstructionsProvider(),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = getClientCapabilities(),
            ),
        )

        runBlocking {
            // Connect client and server
            launch { client.connect(clientTransport) }
            launch { server.createSession(serverTransport) }
        }
    }
}
