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
import kotlinx.coroutines.delay
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
            capabilities = ServerCapabilities(),
        ),
    )

    val serverSession = CompletableDeferred<ServerSession>()
    listOf(
        launch { client.connect(clientTransport) },
        launch { serverSession.complete(server.createSession(serverTransport)) },
    ).joinAll()
    val session = serverSession.await()

    // Register a notification handler so the server reacts when roots change
    session.setNotificationHandler<RootsListChangedNotification>(
        Method.Defined.NotificationsRootsListChanged,
    ) {
        launch {
            val updatedRoots = session.listRoots()
            println("\n[Server] Roots list changed — updated roots:")
            updatedRoots.roots.forEach { root ->
                println("  ${root.name ?: "(unnamed)"}: ${root.uri}")
            }
        }
        CompletableDeferred(Unit)
    }

    // Register roots on the client
    client.addRoot("file:///home/user/projects/my-project", "My Project")
    client.addRoot("file:///home/user/Documents", "Documents")

    // Server queries the client's root list
    val roots = session.listRoots()
    println("Initial roots reported by client:")
    roots.roots.forEach { root ->
        println("  ${root.name}: ${root.uri}")
    }

    // Client adds a root and notifies the server
    client.addRoot("file:///home/user/projects/shared", "Shared Libraries")
    client.sendRootsListChanged()

    // Allow the notification handler to process before shutting down
    delay(500)

    client.close()
    server.close()
}