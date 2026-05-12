package io.modelcontextprotocol.sample.roots

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMcpApi::class)
fun main() = runBlocking {
    val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()

    val client = Client(
        clientInfo = Implementation(name = "roots-demo-client", version = "1.0.0"),
        ClientOptions(
            capabilities = ClientCapabilities(
                roots = ClientCapabilities.Roots(listChanged = true),
            ),
        ),
    )

    val server = Server(
        Implementation(name = "roots-demo-server", version = "1.0.0"),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
        ),
    )

    val serverSessionResult = CompletableDeferred<ServerSession>()

    listOf(
        launch { client.connect(clientTransport) },
        launch { serverSessionResult.complete(server.createSession(serverTransport)) },
    ).joinAll()

    val serverSession = serverSessionResult.await()

    println("=== MCP Roots Demo ===\n")

    serverSession.setNotificationHandler<RootsListChangedNotification>(
        Method.Defined.NotificationsRootsListChanged,
    ) {
        launch {
            println("[Server] Received roots list changed notification — re-fetching roots...")
            val updatedRoots = serverSession.listRoots()
            println("[Server] Updated roots:")
            updatedRoots.roots.forEach { root ->
                println("  - ${root.name ?: "(unnamed)"}: ${root.uri}")
            }
        }
        CompletableDeferred(Unit)
    }

    println("[Client] Adding initial roots...")
    client.addRoot("file:///home/user/projects/frontend", "Frontend Project")
    client.addRoot("file:///home/user/projects/backend", "Backend Project")
    println("[Client] Roots registered: Frontend Project, Backend Project\n")

    println("[Server] Requesting roots from client...")
    val rootsResult = serverSession.listRoots()
    println("[Server] Received roots:")
    rootsResult.roots.forEach { root ->
        println("  - ${root.name}: ${root.uri}")
    }

    println("\n[Client] Adding a new root and sending list changed notification...")
    client.addRoot("file:///home/user/projects/shared-libs", "Shared Libraries")
    client.sendRootsListChanged()

    kotlinx.coroutines.delay(500)

    println("\n[Client] Removing a root and sending list changed notification...")
    client.removeRoot("file:///home/user/projects/backend")
    client.sendRootsListChanged()

    kotlinx.coroutines.delay(500)

    println("\n=== Demo Complete ===")

    client.close()
    server.close()
}