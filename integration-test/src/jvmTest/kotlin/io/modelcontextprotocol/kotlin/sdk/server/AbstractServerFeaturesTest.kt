package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class AbstractServerFeaturesTest {

    protected lateinit var server: Server
    protected lateinit var client: Client

    protected fun addTool(name: String, block: suspend ClientConnection.() -> Unit) {
        server.addTool(name, "Test $name") {
            block()
            CallToolResult(listOf(TextContent("Success")))
        }
    }

    abstract fun getServerCapabilities(): ServerCapabilities

    protected open fun getClientCapabilities(): ClientCapabilities = ClientCapabilities()

    @BeforeEach
    fun setUp() {
        server = Server(Implementation(name = "test server", version = "1.0"), ServerOptions(getServerCapabilities()))
        client = Client(Implementation(name = "test client", version = "1.0"), ClientOptions(getClientCapabilities()))
        runBlocking { connect(server, client) }
    }

    @AfterEach
    fun tearDown() = runBlocking {
        client.close()
        server.close()
    }
}

/** Connects [client] to a new session of [server] over an [InMemoryTransport] pair. */
internal suspend fun connect(server: Server, client: Client): ServerSession {
    val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
    val session = server.createSession(serverTransport)
    client.connect(clientTransport)
    return session
}
