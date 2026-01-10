package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ClientTest {
    @Test
    fun `should initialize with matching protocol version`() = runTest {
        var initialised = false
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
                if (message !is JSONRPCRequest) return
                initialised = true
                val result = InitializeResult(
                    protocolVersion = LATEST_PROTOCOL_VERSION,
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0",
                    ),
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result,
                )

                _onMessage.invoke(response)
            }

            override suspend fun close() {
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0",
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = EmptyJsonObject,
                ),
            ),
        )

        client.connect(clientTransport)
        assertTrue(initialised)
    }

    @Test
    fun `should initialize with supported older protocol version`() = runTest {
        val oldVersion = SUPPORTED_PROTOCOL_VERSIONS[1]
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)

                val result = InitializeResult(
                    protocolVersion = oldVersion,
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0",
                    ),
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result,
                )
                _onMessage.invoke(response)
            }

            override suspend fun close() {
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0",
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = EmptyJsonObject,
                ),
            ),
        )

        client.connect(clientTransport)
        assertEquals(
            Implementation("test", "1.0"),
            client.serverVersion,
        )
    }

    @Test
    fun `should reject unsupported protocol version`() = runTest {
        var closed = false
        val clientTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)

                val result = InitializeResult(
                    protocolVersion = "invalid-version",
                    capabilities = ServerCapabilities(),
                    serverInfo = Implementation(
                        name = "test",
                        version = "1.0",
                    ),
                )

                val response = JSONRPCResponse(
                    id = message.id,
                    result = result,
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
                version = "1.0",
            ),
            options = ClientOptions(),
        )

        assertFailsWith<IllegalStateException>("Server's protocol version is not supported: invalid-version") {
            client.connect(clientTransport)
        }

        assertTrue(closed)
    }

    @Test
    fun `should reject due to non cancellation exception`() = runTest {
        var closed = false
        val failingTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)
                throw IllegalStateException("Test error")
            }

            override suspend fun close() {
                closed = true
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0",
            ),
            options = ClientOptions(),
        )

        val exception = assertFailsWith<IllegalStateException> {
            client.connect(failingTransport)
        }

        assertEquals("Error connecting to transport: Test error", exception.message)

        assertTrue(closed)
    }

    @Test
    fun `should rethrow McpException as is`() = runTest {
        var closed = false
        val failingTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)
                throw McpException(
                    code = -32600,
                    message = "Invalid Request",
                )
            }

            override suspend fun close() {
                closed = true
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0",
            ),
            options = ClientOptions(),
        )

        val exception = assertFailsWith<McpException> {
            client.connect(failingTransport)
        }

        assertEquals(-32600, exception.code)
        assertEquals("MCP error -32600: Invalid Request", exception.message)

        assertTrue(closed)
    }

    @Test
    fun `should rethrow StreamableHttpError as is`() = runTest {
        var closed = false
        val failingTransport = object : AbstractTransport() {
            override suspend fun start() {}

            override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
                if (message !is JSONRPCRequest) return
                check(message.method == Method.Defined.Initialize.value)
                throw StreamableHttpError(
                    code = 500,
                    message = "Internal Server Error",
                )
            }

            override suspend fun close() {
                closed = true
            }
        }

        val client = Client(
            clientInfo = Implementation(
                name = "test client",
                version = "1.0",
            ),
            options = ClientOptions(),
        )

        val exception = assertFailsWith<StreamableHttpError> {
            client.connect(failingTransport)
        }

        assertEquals(500, exception.code)
        assertEquals("Streamable HTTP error: Internal Server Error", exception.message)

        assertTrue(closed)
    }

    @Test
    fun `should respect server capabilities`() = runTest {
        val serverOptions = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(null, null),
                tools = ServerCapabilities.Tools(null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(sampling = EmptyJsonObject),
            ),
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
            ListResourcesResult(resources = emptyList(), nextCursor = null)
        }

        serverSession.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
            ListToolsResult(tools = emptyList(), nextCursor = null)
        }
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
            ServerOptions(capabilities = ServerCapabilities()),
        )

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(listChanged = true),
                ),
            ),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                server.createSession(serverTransport)
                println("Server connected")
            },
        ).joinAll()

        // This should not throw because the client supports roots.listChanged
        client.sendRootsListChanged()

        // Create a new client without the roots.listChanged capability
        val clientWithoutCapability = Client(
            clientInfo = Implementation(name = "test client without capability", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
                //                enforceStrictCapabilities = true // TODO()
            ),
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
                    resources = ServerCapabilities.Resources(listChanged = true, subscribe = null),
                ),
            ),
        )

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()
        // These should not throw
        val jsonObject = buildJsonObject {
            put("name", "John")
            put("age", 30)
            put("isStudent", false)
        }
        serverSession.sendLoggingMessage(
            LoggingMessageNotification(
                params = LoggingMessageNotificationParams(
                    level = LoggingLevel.Info,
                    data = jsonObject,
                ),
            ),
        )
        serverSession.sendResourceListChanged()

        // This should fail because the server doesn't have the tools capability
        val ex = assertFailsWith<IllegalStateException> {
            serverSession.sendToolListChanged()
        }
        assertTrue(ex.message?.contains("Server does not support notifying of tool list changes") == true)
    }

    @Test
    fun `should handle client cancelling a request`() = runTest {
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(
                        listChanged = null,
                        subscribe = null,
                    ),
                ),
            ),
        )

        val def = CompletableDeferred<Unit>()
        val defTimeOut = CompletableDeferred<Unit>()
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()

        serverSession.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, _ ->
            // Simulate delay
            def.complete(Unit)
            try {
                delay(1000)
            } catch (e: CancellationException) {
                defTimeOut.complete(Unit)
                throw e
            }
            ListResourcesResult(resources = emptyList())
            fail("Shouldn't have been called")
        }

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
                capabilities = ServerCapabilities(
                    resources = ServerCapabilities.Resources(
                        listChanged = null,
                        subscribe = null,
                    ),
                ),
            ),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()
        serverSession.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, _ ->
            // Simulate a delayed response
            // Wait ~100ms unless canceled
            try {
                withTimeout(100L) {
                    // Just delay here, if timeout is 0 on the client side, this won't return in time
                    delay(100)
                }
            } catch (_: Exception) {
                // If aborted, just rethrow or return early
            }
            ListResourcesResult(resources = emptyList())
        }

        // Request with 1 msec timeout should fail immediately
        val ex = assertFailsWith<Exception> {
            withTimeout(1) {
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
                version = "1.0",
            ),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    sampling = EmptyJsonObject,
                ),
            ),
        )

        client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { _, _ ->
            CreateMessageResult(
                model = "test-model",
                role = Role.Assistant,
                content = TextContent(
                    text = "Test response",
                ),
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
                tools = ServerCapabilities.Tools(null),
            ),
        )
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            serverOptions,
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(sampling = EmptyJsonObject),
            ),
        )

        var receivedMessage: JSONRPCMessage? = null
        clientTransport.onMessage { msg ->
            receivedMessage = msg
        }

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
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

        val serverListToolsResult = ListToolsResult(
            tools = listOf(
                Tool(
                    name = "testTool",
                    title = "testTool title",
                    description = "testTool description",
                    annotations = null,
                    inputSchema = ToolSchema(),
                    outputSchema = null,
                ),
            ),
            nextCursor = null,
        )

        serverSession.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
            serverListToolsResult
        }

        val serverCapabilities = client.serverCapabilities
        assertEquals(ServerCapabilities.Tools(null), serverCapabilities?.tools)

        val request = JSONRPCRequest(
            method = Method.Defined.ToolsList.value,
        )
        clientTransport.send(request)

        assertIs<JSONRPCResponse>(receivedMessage)
        val receivedAsResponse = receivedMessage as JSONRPCResponse
        assertEquals(request.id, receivedAsResponse.id)
        assertEquals(request.jsonrpc, receivedAsResponse.jsonrpc)
        assertEquals(serverListToolsResult, receivedAsResponse.result)
    }

    @Test
    fun `listRoots returns list of roots`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(
                    roots = ClientCapabilities.Roots(null),
                ),
            ),
        )

        val clientRoots = listOf(
            Root(uri = "file:///test-root", name = "testRoot"),
        )

        client.addRoots(clientRoots)

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(),
            ),
        )

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()

        val clientCapabilities = serverSession.clientCapabilities
        assertEquals(ClientCapabilities.Roots(null), clientCapabilities?.roots)

        val listRootsResult = serverSession.listRoots()

        assertEquals(listRootsResult.roots, clientRoots)
    }

    @Test
    fun `addRoot should throw when roots capability is not supported`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        // Verify that adding a root throws an exception
        val exception = assertFailsWith<IllegalStateException> {
            client.addRoot(uri = "file:///test-root1", name = "testRoot1")
        }
        assertEquals("Client does not support roots capability.", exception.message)
    }

    @Test
    fun `removeRoot should throw when roots capability is not supported`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        // Verify that removing a root throws an exception
        val exception = assertFailsWith<IllegalStateException> {
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
                    roots = ClientCapabilities.Roots(null),
                ),
            ),
        )

        // Add some roots
        client.addRoots(
            listOf(
                Root(uri = "file:///test-root1", name = "testRoot1"),
                Root(uri = "file:///test-root2", name = "testRoot2"),
            ),
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
                    roots = ClientCapabilities.Roots(null),
                ),
            ),
        )

        // Add some roots
        client.addRoots(
            listOf(
                Root(uri = "file:///test-root1", name = "testRoot1"),
                Root(uri = "file:///test-root2", name = "testRoot2"),
            ),
        )

        // Remove multiple roots
        val result = client.removeRoots(
            listOf("file:///test-root1", "file:///test-root2"),
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
                    roots = ClientCapabilities.Roots(listChanged = true),
                ),
            ),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(),
            ),
        )

        // Track notifications
        var rootListChangedNotificationReceived = false

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()
        serverSession.setNotificationHandler<RootsListChangedNotification>(
            Method.Defined.NotificationsRootsListChanged,
        ) {
            rootListChangedNotificationReceived = true
            CompletableDeferred(Unit)
        }

        client.sendRootsListChanged()

        assertTrue(
            rootListChangedNotificationReceived,
            "Notification should be sent when sendRootsListChanged is called",
        )
    }

    @Test
    fun `should reject server elicitation when elicitation capability is not supported`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(),
            ),
        )

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()

        // Verify that creating an elicitation throws an exception
        val exception = assertFailsWith<IllegalStateException> {
            serverSession.createElicitation(
                message = "Please provide your GitHub username",
                requestedSchema = ElicitRequestParams.RequestedSchema(
                    properties = buildJsonObject {
                        putJsonObject("name") {
                            put("type", "string")
                        }
                    },
                    required = listOf("name"),
                ),
            )
        }
        assertEquals(
            "Client does not support elicitation (required for elicitation/create)",
            exception.message,
        )
    }

    @Test
    fun `should handle logging setLevel request`() = runTest {
        val server = Server(
            Implementation(name = "test server", version = "1.0"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    logging = EmptyJsonObject,
                ),
            ),
        )

        val client = Client(
            clientInfo = Implementation(name = "test client", version = "1.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(),
            ),
        )

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val receivedMessages = mutableListOf<LoggingMessageNotification>()
        client.setNotificationHandler<LoggingMessageNotification>(Method.Defined.NotificationsMessage) { notification ->
            receivedMessages.add(notification)
            CompletableDeferred(Unit)
        }

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()

        // Set logging level to warning
        val minLevel = LoggingLevel.Warning
        val result = client.setLoggingLevel(minLevel)
        assertNull(result.meta)

        // Send messages of different levels
        val testMessages = listOf(
            LoggingLevel.Debug to "Debug - should be filtered",
            LoggingLevel.Info to "Info - should be filtered",
            LoggingLevel.Warning to "Warning - should pass",
            LoggingLevel.Error to "Error - should pass",
        )

        testMessages.forEach { (level, message) ->
            serverSession.sendLoggingMessage(
                LoggingMessageNotification(
                    params = LoggingMessageNotificationParams(
                        level = level,
                        data = buildJsonObject { put("message", message) },
                    ),
                ),
            )
        }

        delay(100)

        // Only warning and error should be received
        assertEquals(2, receivedMessages.size, "Should receive only 2 messages (warning and error)")

        // Verify all received messages have severity >= minLevel
        receivedMessages.forEach { message ->
            val messageSeverity = message.params.level.ordinal
            assertTrue(
                messageSeverity >= minLevel.ordinal,
                "Received message with level ${message.params.level} should have severity >= $minLevel",
            )
        }
    }

    @Test
    fun `should handle server elicitation`() = runTest {
        val client = Client(
            Implementation(name = "test client", version = "1.0"),
            ClientOptions(
                capabilities = ClientCapabilities(
                    elicitation = EmptyJsonObject,
                ),
            ),
        )

        val elicitationMessage = "Please provide your GitHub username"
        val requestedSchema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                }
            },
            required = listOf("name"),
        )

        val elicitationResultAction = ElicitResult.Action.Accept
        val elicitationResultContent = buildJsonObject {
            put("name", "octocat")
        }

        client.setElicitationHandler { request ->
            assertEquals(elicitationMessage, request.params.message)
            assertEquals(requestedSchema, request.params.requestedSchema)

            ElicitResult(
                action = elicitationResultAction,
                content = elicitationResultContent,
            )
        }

        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()

        val server = Server(
            serverInfo = Implementation(name = "test server", version = "1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(),
            ),
        )

        val serverSessionResult = CompletableDeferred<ServerSession>()

        listOf(
            launch {
                client.connect(clientTransport)
                println("Client connected")
            },
            launch {
                serverSessionResult.complete(server.createSession(serverTransport))
                println("Server connected")
            },
        ).joinAll()

        val serverSession = serverSessionResult.await()

        val result = serverSession.createElicitation(
            message = elicitationMessage,
            requestedSchema = requestedSchema,
        )

        assertEquals(elicitationResultAction, result.action)
        assertEquals(elicitationResultContent, result.content)
    }
}
