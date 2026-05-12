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
import java.util.concurrent.atomic.AtomicInteger

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
            capabilities = ServerCapabilities(),
        ),
    )

    val serverSessionResult = CompletableDeferred<ServerSession>()

    listOf(
        launch { client.connect(clientTransport) },
        launch { serverSessionResult.complete(server.createSession(serverTransport)) },
    ).joinAll()

    val serverSession = serverSessionResult.await()

    println("=== MCP Roots Demo ===\n")

    val firstNotification = CompletableDeferred<Unit>()
    val secondNotification = CompletableDeferred<Unit>()
    val notificationCount = AtomicInteger(0)

    serverSession.setNotificationHandler<RootsListChangedNotification>(
        Method.Defined.NotificationsRootsListChanged,
    ) {
        launch {
            try {
                println("[Server] Received roots list changed notification — re-fetching roots...")
                val updatedRoots = serverSession.listRoots()
                println("[Server] Updated roots:")
                updatedRoots.roots.forEach { root ->
                    println("  - ${root.name ?: "(unnamed)"}: ${root.uri}")
                }
                when (notificationCount.incrementAndGet()) {
                    1 -> firstNotification.complete(Unit)
                    2 -> secondNotification.complete(Unit)
                }
            } catch (e: Exception) {
                println("[Server] Error handling roots list changed: ${e.message}")
            }
        }
        CompletableDeferred(Unit)
    }

    println("[Client] Adding initial roots...")
    val frontendRoot = java.io.File(System.getProperty("user.home"), "projects/frontend").toPath().toUri().toString()
    val backendRoot = java.io.File(System.getProperty("user.home"), "projects/backend").toPath().toUri().toString()
    client.addRoot(frontendRoot, "Frontend Project")
    client.addRoot(backendRoot, "Backend Project")
    println("[Client] Roots registered: Frontend Project, Backend Project\n")

    println("[Server] Requesting roots from client...")
    val rootsResult = serverSession.listRoots()
    println("[Server] Received roots:")
    rootsResult.roots.forEach { root ->
        println("  - ${root.name ?: "(unnamed)"}: ${root.uri}")
    }

    println("\n[Client] Adding a new root and sending list changed notification...")
    val sharedLibsRoot = java.io.File(System.getProperty("user.home"), "projects/shared-libs").toPath().toUri().toString()
    client.addRoot(sharedLibsRoot, "Shared Libraries")
    client.sendRootsListChanged()
    firstNotification.await()

    println("\n[Client] Removing a root and sending list changed notification...")
    client.removeRoot(backendRoot)
    client.sendRootsListChanged()
    secondNotification.await()

    println("\n=== Demo Complete ===")

    server.close()
    client.close()
}