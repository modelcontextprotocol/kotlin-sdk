package io.modelcontextprotocol.kotlin.sdk.integration

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.CacheEntry
import io.modelcontextprotocol.kotlin.sdk.client.CacheKey
import io.modelcontextprotocol.kotlin.sdk.client.CacheMode
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.InMemoryResponseCacheStore
import io.modelcontextprotocol.kotlin.sdk.client.ResponseCacheStore
import io.modelcontextprotocol.kotlin.sdk.client.VersionNegotiationMode
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The client-side response cache (SEP-2549, revision 2026-07-28): what it reuses, what it refuses to
 * reuse, and what stales it.
 *
 * Driven through [Client] over an in-memory pair. Reuse is observed by counting how many times the
 * server's `tools/list` handler ran: the round trip either happened or it did not, and that count is
 * the only honest witness to which.
 */
@OptIn(ExperimentalMcpApi::class, ExperimentalTime::class)
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
        val announced = fixture.client.onToolListChanged()

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
    fun `a listing in flight when the list changes is not served after the change`() = runBlocking {
        val held = connectHoldingFirstListing()

        val inFlight = async { held.fixture.client.listTools() }
        held.changeListWhileFirstListingIsHeld()
        held.releaseFirstListing.complete(Unit)
        inFlight.await().tools.single().name shouldBe "before-change"

        held.fixture.client.listTools().tools.single().name shouldBe "after-change"
        held.fixture.client.close()
    }

    @Test
    fun `a listing that lands after the change leaves other partitions cached`() = runBlocking {
        val store = InMemoryResponseCacheStore()
        val otherPartition = CacheKey(method = Method.Defined.ToolsList.value, partition = "other-client")
        val held = connectHoldingFirstListing(store)

        val inFlight = async { held.fixture.client.listTools() }
        held.changeListWhileFirstListingIsHeld()
        val otherEntry = CacheEntry(
            result = ListToolsResult(tools = emptyList()),
            expiresAt = Clock.System.now() + 60.seconds,
            cacheScope = CacheScope.Private,
        )
        store.put(otherPartition, otherEntry)
        held.releaseFirstListing.complete(Unit)
        inFlight.await().tools.single().name shouldBe "before-change"

        store.get(otherPartition) shouldBe otherEntry
        held.fixture.client.close()
    }

    @Test
    fun `a continuation page is never served from or filed under the cursorless entry`() = runBlocking {
        val fixture = connect { request, _ ->
            // The first page names a cursor; a page-2 result is deliberately distinguishable.
            val firstPage = request.params?.cursor == null
            toolListing(
                name = if (firstPage) "page-1" else "page-2",
                ttlMs = 60_000,
                nextCursor = "p2".takeIf { firstPage },
            )
        }
        val client = fixture.client

        client.listTools() // page 1, cached under the cursorless key
        val secondPage = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = "p2")))
        val secondPageAgain = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = "p2")))
        val firstPageAgain = client.listTools() // must still be the cached page 1, not page 2

        // Every cursor-bearing call round-trips; only the first cursorless call did.
        fixture.listCalls.get() shouldBe 3
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

    private class Fixture(
        val server: Server,
        val session: ServerSession,
        val client: Client,
        val listCalls: AtomicInteger,
    )

    /**
     * A connection whose first `tools/list` is held until [releaseFirstListing] completes; it answers
     * `before-change`, and every later call answers `after-change`.
     */
    private class HeldFirstListing(
        val fixture: Fixture,
        val firstListingHeld: CompletableDeferred<Unit>,
        val releaseFirstListing: CompletableDeferred<Unit>,
        val invalidationApplied: CompletableDeferred<Unit>,
    ) {
        /** Announces a list change once the first listing is in flight and waits for the client to apply it. */
        suspend fun changeListWhileFirstListingIsHeld() {
            withTimeout(5.seconds) { firstListingHeld.await() }
            fixture.session.sendToolListChanged()
            withTimeout(5.seconds) { invalidationApplied.await() }
        }
    }

    private suspend fun connectHoldingFirstListing(
        store: ResponseCacheStore = InMemoryResponseCacheStore(),
    ): HeldFirstListing {
        val firstListingHeld = CompletableDeferred<Unit>()
        val releaseFirstListing = CompletableDeferred<Unit>()
        val fixture = connect(store = store) { _, call ->
            if (call == 1) {
                firstListingHeld.complete(Unit)
                releaseFirstListing.await()
            }
            toolListing(name = if (call == 1) "before-change" else "after-change", ttlMs = 60_000)
        }
        val invalidationApplied = fixture.client.onToolListChanged()
        return HeldFirstListing(fixture, firstListingHeld, releaseFirstListing, invalidationApplied)
    }

    /** A connected pair whose `tools/list` reports [ttlMs] as how long its listing stays fresh. */
    private suspend fun connect(
        ttlMs: Long,
        negotiation: VersionNegotiationMode = VersionNegotiationMode.Auto,
    ): Fixture = connect(negotiation = negotiation) { _, _ -> toolListing(name = "echo", ttlMs = ttlMs) }

    /** A connected pair whose `tools/list` is answered by [listTools], given the 1-based call number. */
    private suspend fun connect(
        negotiation: VersionNegotiationMode = VersionNegotiationMode.Auto,
        store: ResponseCacheStore = InMemoryResponseCacheStore(),
        listTools: suspend (request: ListToolsRequest, call: Int) -> ListToolsResult,
    ): Fixture {
        val listCalls = AtomicInteger()
        val server = cacheServer()
        val client = Client(
            clientInfo = Implementation("cache-client", "1.0"),
            options = ClientOptions(
                versionNegotiation = negotiation,
                responseCache = store,
            ),
        )
        val session = connectSession(client, server, listCalls, listTools)
        return Fixture(server, session, client, listCalls)
    }

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
        connectSession(client, server, listCalls) { _, _ -> toolListing(name = tool, ttlMs = ttlMs, scope = scope) }
    }

    private suspend fun connectSession(
        client: Client,
        server: Server,
        listCalls: AtomicInteger,
        listTools: suspend (request: ListToolsRequest, call: Int) -> ListToolsResult,
    ): ServerSession {
        lateinit var session: ServerSession
        val (clientTransport, serverTransport) = ChannelTransport.createLinkedPair()
        runBlocking {
            launch {
                session = server.createSession(serverTransport)
                session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { request, _ ->
                    listTools(request, listCalls.incrementAndGet())
                }
            }
            launch { client.connect(clientTransport) }
        }
        return session
    }

    private fun toolListing(
        name: String,
        ttlMs: Long,
        nextCursor: String? = null,
        scope: CacheScope = CacheScope.Private,
    ): ListToolsResult = ListToolsResult(
        tools = listOf(
            Tool(
                name = name,
                description = "the $name tool",
                inputSchema = ToolSchema(properties = EmptyJsonObject, required = null),
            ),
        ),
        nextCursor = nextCursor,
        ttlMs = ttlMs,
        cacheScope = scope,
    )

    /** Completes once the client has handled a tools list-changed notification. */
    private fun Client.onToolListChanged(): CompletableDeferred<Unit> {
        val announced = CompletableDeferred<Unit>()
        setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
            announced.complete(Unit)
            CompletableDeferred(Unit)
        }
        return announced
    }
}
