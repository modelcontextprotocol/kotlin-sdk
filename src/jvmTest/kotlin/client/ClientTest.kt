package client

import io.mockk.coEvery
import io.mockk.spyk
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class ClientTest {
    @Test
    fun `should initialize with matching protocol version`() = runTest {
        var initialised = false
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage) {
                if (message !is JSONRPCRequest) return
                initialised = true
                val result = InitializeResult(
                    protocolVersion = LATEST_PROTOCOL_VERSION,
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0"
                    )
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result
                )

                _onMessage.invoke(response)
            }

            override suspend fun close() {
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0"
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = EmptyJsonObject
                )
            )
        )

        client.connect(clientTransport)
        assertTrue(initialised)
    }

    @Test
    fun `should initialize with supported older protocol version`() = runTest {
        val OLD_VERSION = SUPPORTED_PROTOCOL_VERSIONS[1]
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)

                val result = InitializeResult(
                    protocolVersion = OLD_VERSION,
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0"
                    )
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result
                )
                _onMessage.invoke(response)
            }

            override suspend fun close() {
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0"
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = EmptyJsonObject
                )
            )
        )

        client.connect(clientTransport)
        assertEquals(
            Implementation("test", "1.0"),
            client.serverVersion
        )
    }

    @Test
    fun `should reject unsupported protocol version`() = runTest {
        var closed = false
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)

                val result = InitializeResult(
                    protocolVersion = "invalid-version",
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0"
                    )
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result
                )

                _onMessage.invoke(response)
            }

            override suspend fun close() {
                closed = true
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0"
            ),
            options = ClientOptions()
        )

        assertFailsWith<IllegalStateException>("Server's protocol version is not supported: invalid-version") {
            client.connect(clientTransport)
        }

        assertTrue(closed)
    }

    @Test
    fun `should reject due to non cancellation exception`() = runTest {
        var closed = false
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)

                val result = InitializeResult(
                    protocolVersion = LATEST_PROTOCOL_VERSION,
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0"
                    )
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result
                )

                _onMessage.invoke(response)
            }

            override suspend fun close() {
                closed = true
            }
        }

        val mockClient = spyk(
            Client(
                clientInfo = Implementation(
                    name = "test client",
                    version = "1.0"
                ),
                options = ClientOptions()
            )
        )

        coEvery{
            mockClient.request<InitializeResult>(any())
        } throws IllegalStateException("Test error")

        val exception = assertFailsWith<IllegalStateException> {
            mockClient.connect(clientTransport)
        }

        assertEquals("Error connecting to transport: Test error", exception.message)

        assertTrue(closed)
    }

    @Test
    fun `should respect server capabilities`() = runTest {
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(null, null),
                tools = ServerCapabilities.Tools(null)
            )
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions
        )

        server.setRequestHandler<InitializeRequest>(Method.Defined.Initialize) { request, _ ->
            InitializeResult(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(null, null),
                    tools = ServerCapabilities.Tools(null)
                ),
                serverInfo = Implementation(name = "test", version = "1.0")
            )
        }

        server.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { request, _ ->
            ListResourcesResult(resources = emptyList(), nextCursor = null)
        }

        server.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { request, _ ->
            ListToolsResult(tools = emptyList(), nextCursor = null)
        }

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(sampling = EmptyJsonObject),
            )
        )

        listOf(
            launch {
                client.connect(clientTransport)
            },
            launch {
                server.connect(serverTransport)
            }
        ).joinAll()

        // Server supports resources and tools, but not prompts
        val caps = client.serverCapabilities
        assertEquals(ServerCapabilities.Resources(null, null), caps?.resources)
        assertEquals(ServerCapabilities.Tools(null), caps?.tools)
        assertTrue(caps?.prompts == null) // or check that prompts are absent

        // These should not throw
        client.listResources()
        client.listTools()

        // This should fail because prompts are not supported
        val ex = assertFailsWith<IllegalStateException> {
            client.listPrompts()
        }
        assertTrue(ex.message?.contains("Server does not support prompts") == true)
    }

    @Test
    fun `should respect client notification capabilities`() = runTest {
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(capabilities = ServerCapabilities())
        )

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(listChanged = true)
                )
            )
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                server.connect(serverTransport)
                println("Server connected")
            }
        ).joinAll()

        // This should not throw because the client supports roots.listChanged
        client.sendRootsListChanged()

        // Create a new client without the roots.listChanged capability
        val clientWithoutCapability = Client(
            clientInfo = Implementation(name = "test client without capability", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
                //                enforceStrictCapabilities = true // TODO()
            )
        )

        clientWithoutCapability.connect(clientTransport)
        // Using the same transport pair might not be realistic - in a real scenario you'd create another pair.
        // Adjust if necessary.

        // This should fail
        val ex = assertFailsWith<IllegalStateException> {
            clientWithoutCapability.sendRootsListChanged()
        }
        assertTrue(ex.message?.startsWith("Client does not support") == true)
    }

    @Test
    fun `should respect server notification capabilities`() = runTest {
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    logging = EmptyJsonObject,
                    resources = ServerCapabilities.Resources(listChanged = true, subscribe = null)
                )
            )
        )

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities()
            )
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                server.connect(serverTransport)
                println("Server connected")
            }
        ).joinAll()

        // These should not throw
        val jsonObject = buildJsonObject {
            put("name", "John")
            put("age", 30)
            put("isStudent", false)
        }
        server.sendLoggingMessage(
            LoggingMessageNotification(
                level = LoggingLevel.info,
                data = jsonObject
            )
        )
        server.sendResourceListChanged()

        // This should fail because the server doesn't have the tools capability
        val ex = assertFailsWith<IllegalStateException> {
            server.sendToolListChanged()
        }
        assertTrue(ex.message?.contains("Server does not support notifying of tool list changes") == true)
    }

    @Test
    fun `should handle client cancelling a request`() = runTest {
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(resources = ServerCapabilities.Resources(listChanged = null, subscribe = null))
            )
        )

        val def = CompletableDeferred<Unit>()
        val defTimeOut = CompletableDeferred<Unit>()
        server.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, extra ->
            // Simulate delay
            def.complete(Unit)
            try {
                kotlinx.coroutines.delay(1000)
            } catch (e: CancellationException) {
                defTimeOut.complete(Unit)
                throw e
            }
            fail("Shouldn't have been called")
            ListResourcesResult(resources = emptyList())
        }

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(capabilities = ClientCapabilities())
        )

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                server.connect(serverTransport)
                println("Server connected")
            }
        ).joinAll()

        val defCancel = CompletableDeferred<Unit>()
        val job = launch {
            try {
                client.listResources()
            } catch (e: CancellationException) {
                defCancel.complete(Unit)
                assertEquals("Cancelled by test", e.message)
            }
        }
        def.await()
        runCatching { job.cancel("Cancelled by test") }
        defCancel.await()
        defTimeOut.await()
    }

    @Test
    fun `should handle request timeout`() = runTest {
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(resources = ServerCapabilities.Resources(listChanged = null, subscribe = null))
            )
        )

        server.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, extra ->
            // Simulate a delayed response
            // Wait ~100ms unless canceled
            try {
                kotlinx.coroutines.withTimeout(100L) {
                    // Just delay here, if timeout is 0 on the client side, this won't return in time
                    kotlinx.coroutines.delay(100)
                }
            } catch (_: Exception) {
                // If aborted, just rethrow or return early
            }
            ListResourcesResult(resources = emptyList())
        }

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(capabilities = ClientCapabilities())
        )

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                server.connect(serverTransport)
                println("Server connected")
            }
        ).joinAll()

        // Request with 1 msec timeout should fail immediately
        val ex = assertFailsWith<Exception> {
            kotlinx.coroutines.withTimeout(1) {
                client.listResources()
            }
        }
        assertTrue(ex is TimeoutCancellationException)
    }

    @Test
    fun `should only allow setRequestHandler for declared capabilities`() = runTest {
        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0"
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = EmptyJsonObject
                )
            )
        )

        client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { request, _ ->
            CreateMessageResult(
                model = "test-model",
                role = Role.assistant,
                content = TextContent(
                    text = "Test response"
                )
            )
        }

        assertFailsWith<IllegalStateException>("Client does not support roots capability (required for RootsList)") {
            client.setRequestHandler<ListRootsRequest>(Method.Defined.RootsList) { _, _ -> null }
        }
    }

    @Test
    fun `JSONRPCRequest with ToolsList method and default params returns list of tools`() = runTest {
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(null)
            )
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions
        )

        server.setRequestHandler<InitializeRequest>(Method.Defined.Initialize) { request, _ ->
            InitializeResult(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(null, null),
                    tools = ServerCapabilities.Tools(null)
                ),
                serverInfo = Implementation(name = "test", version = "1.0")
            )
        }
        val serverListToolsResult = ListToolsResult(
            tools = listOf(
                Tool(
                    name = "testTool",
                    description = "testTool description",
                    annotations = null,
                    inputSchema = Tool.Input()
                )
            ), nextCursor = null
        )

        server.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { request, _ ->
            serverListToolsResult
        }

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(sampling = EmptyJsonObject),
            )
        )

        var receivedMessage: JSONRPCMessage? = null
        clientTransport.onMessage { msg ->
            receivedMessage = msg
        }

        listOf(
            launch {
                client.connect(clientTransport)
            },
            launch {
                server.connect(serverTransport)
            }
        ).joinAll()

        val serverCapabilities = client.serverCapabilities
        assertEquals(ServerCapabilities.Tools(null), serverCapabilities?.tools)

        val request = JSONRPCRequest(
            method = Method.Defined.ToolsList.value
        )
        clientTransport.send(request)

        assertInstanceOf<JSONRPCResponse>(receivedMessage)
        val receivedAsResponse = receivedMessage as JSONRPCResponse
        assertEquals(request.id, receivedAsResponse.id)
        assertEquals(request.jsonrpc, receivedAsResponse.jsonrpc)
        assertEquals(serverListToolsResult, receivedAsResponse.result)
        assertEquals(null, receivedAsResponse.error)
    }

    @Test
    fun `listRoots returns list of roots`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(null)
                )
            )
        )

        val clientRoots = listOf(
            Root(uri = "file:///test-root", name = "testRoot")
        )

        client.addRoots(clientRoots)

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities()
            )
        )

        listOf(
            launch { client.connect(clientTransport) },
            launch { server.connect(serverTransport) }
        ).joinAll()

        val clientCapabilities = server.clientCapabilities
        assertEquals(ClientCapabilities.Roots(null), clientCapabilities?.roots)

        val listRootsResult = server.listRoots()

        assertEquals(listRootsResult.roots, clientRoots)
    }

    @Test
    fun `addRoot should throw when roots capability is not supported`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities()
            )
        )

        // Verify that adding a root throws an exception
        val exception = assertThrows<IllegalStateException> {
            client.addRoot(uri = "file:///test-root1", name = "testRoot1")
        }
        assertEquals("Client does not support roots capability.", exception.message)
    }

    @Test
    fun `removeRoot should throw when roots capability is not supported`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities()
            )
        )

        // Verify that removing a root throws an exception
        val exception = assertThrows<IllegalStateException> {
            client.removeRoot(uri = "file:///test-root1")
        }
        assertEquals("Client does not support roots capability.", exception.message)
    }

    @Test
    fun `removeRoot should remove a root`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(null)
                )
            )
        )

        // Add some roots
        client.addRoots(
            listOf(
                Root(uri = "file:///test-root1", name = "testRoot1"),
                Root(uri = "file:///test-root2", name = "testRoot2"),
            )
        )

        // Remove a root
        val result = client.removeRoot("file:///test-root1")

        // Verify the root was removed
        assertTrue(result, "Root should be removed successfully")
    }

    @Test
    fun `removeRoots should remove multiple roots`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(null)
                )
            )
        )

        // Add some roots
        client.addRoots(
            listOf(
                Root(uri = "file:///test-root1", name = "testRoot1"),
                Root(uri = "file:///test-root2", name = "testRoot2"),
            )
        )

        // Remove multiple roots
        val result = client.removeRoots(
            listOf("file:///test-root1", "file:///test-root2")
        )

        // Verify the root was removed
        assertEquals(2, result, "Both roots should be removed")
    }

    @Test
    fun `sendRootsListChanged should notify server`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(listChanged = true)
                )
            )
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities()
            )
        )

        // Track notifications
        var rootListChangedNotificationReceived = false
        server.setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) {
            rootListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        listOf(
            launch { client.connect(clientTransport) },
            launch { server.connect(serverTransport) }
        ).joinAll()

        client.sendRootsListChanged()

        assertTrue(
            rootListChangedNotificationReceived,
            "Notification should be sent when sendRootsListChanged is called"
        )
    }
}
