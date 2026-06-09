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
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path

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

    val serverSession = CompletableDeferred<ServerSession>()
    listOf(
        launch { client.connect(clientTransport) },
        launch { serverSession.complete(server.createSession(serverTransport)) },
    ).joinAll()
    val session = serverSession.await()

    val firstNotification = CompletableDeferred<Unit>()
    val secondNotification = CompletableDeferred<Unit>()

    session.setNotificationHandler<RootsListChangedNotification>(
        Method.Defined.NotificationsRootsListChanged,
    ) { _ ->
        launch {
            try {
                val updatedRoots = session.listRoots()
                println("\n[Server] Roots list changed — updated roots:")
                updatedRoots.roots.forEach { root ->
                    println("  ${root.name ?: "(unnamed)"}: ${root.uri}")
                }
            } catch (e: Exception) {
                println("[Server] Error fetching updated roots: ${e.message}")
            } finally {
                if (!firstNotification.isCompleted) {
                    firstNotification.complete(Unit)
                } else if (!secondNotification.isCompleted) {
                    secondNotification.complete(Unit)
                }
            }
        }
        CompletableDeferred(Unit)
    }

    try {
        val homeDir = Path.of(System.getProperty("user.home"))

        client.addRoot(homeDir.resolve("projects/my-project").toUri().toString(), "My Project")
        client.addRoot(homeDir.resolve("Documents").toUri().toString(), "Documents")

        val roots = session.listRoots()
        println("Initial roots reported by client:")
        roots.roots.forEach { root ->
            println("  ${root.name ?: "(unnamed)"}: ${root.uri}")
        }

        val sharedRootUri = homeDir.resolve("projects/shared").toUri().toString()
        client.addRoot(sharedRootUri, "Shared Libraries")
        client.sendRootsListChanged()

        val firstResult = withTimeoutOrNull(5000L) { firstNotification.await() }
        if (firstResult == null) {
            println("[Warning] Timed out waiting for server to process first roots change")
        }

        println("\n[Client] Removing a root and sending list changed notification...")
        client.removeRoot(sharedRootUri)
        client.sendRootsListChanged()

        val secondResult = withTimeoutOrNull(5000L) { secondNotification.await() }
        if (secondResult == null) {
            println("[Warning] Timed out waiting for server to process second roots change")
        }
    } finally {
        client.close()
        server.close()
    }
}