package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.CacheMode
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.InMemoryResponseCacheStore
import io.modelcontextprotocol.kotlin.sdk.client.ResponseCacheStore
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
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
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
    fun `a continuation page is never served from or filed under the cursorless entry`() = runBlocking {
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
                versionNegotiation = VersionNegotiationMode.Auto,
                responseCache = InMemoryResponseCacheStore(),
            ),
        )
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        runBlocking {
            launch {
                val session = server.createSession(serverTransport)
                session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { request, _ ->
                    listCalls.incrementAndGet()
                    // The first page names a cursor; a page-2 result is deliberately distinguishable.
                    val page = if (request.params?.cursor == null) "page-1" else "page-2"
                    ListToolsResult(
                        tools = listOf(
                            Tool(
                                name = page,
                                description = "the $page tool",
                                inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
                            ),
                        ),
                        nextCursor = "p2".takeIf { request.params?.cursor == null },
                        ttlMs = 60_000,
                        cacheScope = CacheScope.Private,
                    )
                }
            }
            launch { client.connect(clientTransport) }
        }

        client.listTools() // page 1, cached under the cursorless key
        val secondPage = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = "p2")))
        val secondPageAgain = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = "p2")))
        val firstPageAgain = client.listTools() // must still be the cached page 1, not page 2

        // Every cursor-bearing call round-trips; only the first cursorless call did.
        listCalls.get() shouldBe 3
        secondPage.tools.single().name shouldBe "page-2"
        secondPageAgain.tools.single().name shouldBe "page-2"
        firstPageAgain.tools.single().name shouldBe "page-1"
        client.close()
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

    @Test
    fun `clients sharing a store each get their own server's listing`() = runBlocking {
        val store = InMemoryResponseCacheStore()
        val alphaClient = cachingClient(store)
        val bravoClient = cachingClient(store)
        connectSession(alphaClient, cacheServer("alpha-server"), tool = "alpha", scope = CacheScope.Public)
        connectSession(bravoClient, cacheServer("bravo-server"), tool = "bravo", scope = CacheScope.Public)

        alphaClient.listTools().tools.single().name shouldBe "alpha"
        bravoClient.listTools().tools.single().name shouldBe "bravo"
        alphaClient.close()
        bravoClient.close()
    }

    @Test
    fun `a private entry is not served to another client sharing the store`() = runBlocking {
        val store = InMemoryResponseCacheStore()
        val alice = cachingClient(store)
        val bob = cachingClient(store)
        connectSession(alice, cacheServer(), tool = "alice-private", scope = CacheScope.Private)
        connectSession(bob, cacheServer(), tool = "bob-private", scope = CacheScope.Private)

        alice.listTools().tools.single().name shouldBe "alice-private"
        bob.listTools().tools.single().name shouldBe "bob-private"
        alice.close()
        bob.close()
    }

    @Test
    fun `a reconnect to another server does not serve the previous server's listing`() = runBlocking {
        val client = cachingClient(InMemoryResponseCacheStore())

        connectSession(client, cacheServer("first-server"), tool = "first", scope = CacheScope.Public)
        client.listTools().tools.single().name shouldBe "first"
        client.close()
        connectSession(client, cacheServer("second-server"), tool = "second", scope = CacheScope.Public)

        client.listTools().tools.single().name shouldBe "second"
        client.close()
    }

    @Test
    fun `clients configured with the same partition share entries`() = runBlocking {
        val store = InMemoryResponseCacheStore()
        val listCalls = AtomicInteger()
        val first = cachingClient(store, partition = "cache-server/alice")
        val second = cachingClient(store, partition = "cache-server/alice")
        connectSession(first, cacheServer(), tool = "echo", scope = CacheScope.Private, listCalls = listCalls)
        connectSession(second, cacheServer(), tool = "echo", scope = CacheScope.Private, listCalls = listCalls)

        first.listTools()
        second.listTools()

        listCalls.get() shouldBe 1
        first.close()
        second.close()
    }

    private class Fixture(val server: Server, val client: Client, val listCalls: AtomicInteger)

    private fun cacheServer(name: String = "cache-server") = Server(
        serverInfo = Implementation(name, "1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
        ),
    )

    private fun cachingClient(store: ResponseCacheStore, partition: String = "") = Client(
        clientInfo = Implementation("cache-client", "1.0"),
        options = ClientOptions(
            versionNegotiation = VersionNegotiationMode.Auto,
            responseCache = store,
            cachePartition = partition,
        ),
    )

    private suspend fun connectSession(
        client: Client,
        server: Server,
        tool: String,
        scope: CacheScope,
        ttlMs: Long = 60_000,
        listCalls: AtomicInteger = AtomicInteger(),
    ) {
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        runBlocking {
            launch {
                val session = server.createSession(serverTransport)
                session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
                    listCalls.incrementAndGet()
                    ListToolsResult(
                        tools = listOf(
                            Tool(
                                name = tool,
                                description = "the $tool tool",
                                inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
                            ),
                        ),
                        ttlMs = ttlMs,
                        cacheScope = scope,
                    )
                }
            }
            launch { client.connect(clientTransport) }
        }
    }

    private suspend fun connect(
        ttlMs: Long,
        negotiation: VersionNegotiationMode = VersionNegotiationMode.Auto,
    ): Fixture {
        val listCalls = AtomicInteger()
        val server = cacheServer()
        val client = Client(
            clientInfo = Implementation("cache-client", "1.0"),
            options = ClientOptions(
                versionNegotiation = negotiation,
                responseCache = InMemoryResponseCacheStore(),
            ),
        )
        connectSession(client, server, tool = "echo", scope = CacheScope.Private, ttlMs = ttlMs, listCalls = listCalls)
        return Fixture(server, client, listCalls)
    }
}
