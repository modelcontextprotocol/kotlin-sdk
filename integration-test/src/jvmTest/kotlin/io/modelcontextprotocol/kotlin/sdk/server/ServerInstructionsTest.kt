package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ServerInstructionsTest {

    private val serverInfo = Implementation(name = "test server", version = "1.0")
    private val options = ServerOptions(capabilities = ServerCapabilities())

    @Test
    fun `instructions should be resolved for every new session`() = runTest {
        var sessions = 0
        val dynamic = Server(serverInfo, options, instructionsProvider = { "instructions #${++sessions}" })

        connectedClient(dynamic).serverInstructions shouldBe "instructions #1"
        connectedClient(dynamic).serverInstructions shouldBe "instructions #2"
        connectedClient(Server(serverInfo, options, instructions = "static")).serverInstructions shouldBe "static"
        connectedClient(Server(serverInfo, options)).serverInstructions.shouldBeNull()
    }

    private suspend fun connectedClient(server: Server): Client =
        Client(Implementation(name = "test client", version = "1.0")).also { connect(server, it) }
}
