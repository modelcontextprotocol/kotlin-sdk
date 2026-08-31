package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.CacheMode
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.InMemoryResponseCacheStore
import io.modelcontextprotocol.kotlin.sdk.client.VersionNegotiationMode
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.CacheScope
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The client-side response cache (SEP-2549, revision 2026-07-28): what it reuses, what it refuses to
 * reuse, and what stales it.
 *
 * Driven through [Client] over an in-memory pair. Reuse is observed by counting how many times the
 * server's `tools/list` handler ran: the round trip either happened or it did not, and that count is
 * the only honest witness to which.
 */
@OptIn(ExperimentalMcpApi::class)
class ResponseCacheTest {

    @Test
    fun `a fresh cached listing is served without a round trip`() = runBlocking {
        val fixture = connect(ttlMs = 60_000)

        fixture.client.listTools()
        fixture.client.listTools()

        fixture.listCalls.get() shouldBe 1
        fixture.client.close()
    }

    @Test
    fun `a listing the server marked immediately stale is fetched again`() = runBlocking {
        // `ttlMs = 0` is the conservative default a handler that says nothing produces.
        val fixture = connect(ttlMs = 0)

        fixture.client.listTools()
        fixture.client.listTools()

        fixture.listCalls.get() shouldBe 2
        fixture.client.close()
    }

    @Test
    fun `a refresh asks the server even while a fresh entry is held`() = runBlocking {
        val fixture = connect(ttlMs = 60_000)

        fixture.client.listTools()
        fixture.client.listTools(cacheMode = CacheMode.Refresh)
        fixture.client.listTools()

        // The refresh both bypassed the entry and replaced it, so the third call is served from it.
        fixture.listCalls.get() shouldBe 2
        fixture.client.close()
    }

    @Test
    fun `a tools list-changed notification stales the cached listing`() = runBlocking {
        val fixture = connect(ttlMs = 60_000)
        val announced = CompletableDeferred<Unit>()
        fixture.client.setNotificationHandler<ToolListChangedNotification>(
            Method.Defined.NotificationsToolsListChanged,
        ) {
            announced.complete(Unit)
            CompletableDeferred(Unit)
        }

        fixture.client.listTools()
        fixture.server.addTool(
            name = "added-later",
            description = "arrives after the listing was cached",
            inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
        ) { error("registered only to announce a catalogue change") }
        // Invalidation runs at the router, ahead of handler lookup, so this handler completing
        // proves the cache was already staled.
        withTimeout(5.seconds) { announced.await() }
        fixture.client.listTools()

        fixture.listCalls.get() shouldBe 2
        fixture.client.close()
    }

    @Test
    fun `a handshake connection caches nothing`() = runBlocking {
        val fixture = connect(ttlMs = 60_000, negotiation = VersionNegotiationMode.Legacy)

        fixture.client.listTools()
        fixture.client.listTools()

        // The directives saying for how long and for whom exist only on the other lifecycle, so a
        // handshake result carries no terms this client is entitled to honour.
        fixture.listCalls.get() shouldBe 2
        fixture.client.close()
    }

    private class Fixture(val server: Server, val client: Client, val listCalls: AtomicInteger)

    /** A connected pair whose `tools/list` reports [ttlMs] as how long its listing stays fresh. */
    private suspend fun connect(
        ttlMs: Long,
        negotiation: VersionNegotiationMode = VersionNegotiationMode.Auto,
    ): Fixture {
        val listCalls = AtomicInteger()
        val server = Server(
            serverInfo = Implementation("cache-server", "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
            ),
        )
        val client = Client(
            clientInfo = Implementation("cache-client", "1.0"),
            options = ClientOptions(
                versionNegotiation = negotiation,
                responseCache = InMemoryResponseCacheStore(),
            ),
        )

        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        runBlocking {
            launch {
                val session = server.createSession(serverTransport)
                session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
                    listCalls.incrementAndGet()
                    ListToolsResult(
                        tools = listOf(
                            Tool(
                                name = "echo",
                                description = "echoes",
                                inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
                            ),
                        ),
                        ttlMs = ttlMs,
                        cacheScope = CacheScope.Private,
                    )
                }
            }
            launch { client.connect(clientTransport) }
        }
        return Fixture(server, client, listCalls)
    }
}
