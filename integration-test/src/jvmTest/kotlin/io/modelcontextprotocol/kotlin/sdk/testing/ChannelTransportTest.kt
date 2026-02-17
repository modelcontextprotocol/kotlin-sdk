package io.modelcontextprotocol.kotlin.sdk.testing

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ChannelTransportTest {

    @Test
    fun `should connect and list resources`(): Unit = runBlocking {
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
        )

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()
        serverSession.setRequestHandler<InitializeRequest>(Method.Defined.Initialize) { _, _ ->
            InitializeResult(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(null, null),
                    tools = ServerCapabilities.Tools(null),
                ),
                serverInfo = Implementation(name = "test", version = "1.0"),
            )
        }

        serverSession.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, _ ->
            ListResourcesResult(
                resources = listOf(
                    Resource(
                        uri = "/foo/bar",
                        name = "foo-bar-resource",
                    ),
                ),
            )
        }

        // These should not throw
        client.listResources() shouldNotBeNull {
            this.resources shouldHaveSize 1
        }

        client.close()
        server.close()
    }
}
