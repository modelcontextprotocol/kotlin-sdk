package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.InMemoryTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestURLParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotification
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EnumOption
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
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
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UrlElicitationRequiredException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test

class ClientTest {

    @Test
    fun `should initialize with supported older protocol version`() = runTest {
        val client = newClient()

        client.connect(InitializeTransport { initializeResult(SUPPORTED_PROTOCOL_VERSIONS[1]) })

        client.serverVersion shouldBe Implementation(name = "test server", version = "1.0")
    }

    @Test
    fun `connect should close the transport and surface initialization failures`() = runTest {
        val cases = listOf(
            ConnectFailure("unsupported protocol version", { initializeResult("invalid-version") }) {
                it.shouldBeInstanceOf<IllegalStateException>()
                it.message shouldBe
                    "Error connecting to transport: Server's protocol version is not supported: invalid-version"
            },
            ConnectFailure("unexpected exception is wrapped", { error("Test error") }) {
                it.shouldBeInstanceOf<IllegalStateException>()
                it.message shouldBe "Error connecting to transport: Test error"
            },
            ConnectFailure("McpException is rethrown", { throw McpException(-32600, "Invalid Request") }) {
                it.shouldBeInstanceOf<McpException>().code shouldBe -32600
                it.message shouldBe "Invalid Request"
            },
            ConnectFailure("StreamableHttpError is rethrown", { throw StreamableHttpError(500, "Server Error") }) {
                it.shouldBeInstanceOf<StreamableHttpError>().code shouldBe 500
                it.message shouldBe "Streamable HTTP error: Server Error"
            },
            ConnectFailure("SerializationException is rethrown", { throw SerializationException("malformed") }) {
                it.shouldBeInstanceOf<SerializationException>()
                it.message shouldBe "malformed"
            },
        )

        for (case in cases) {
            withClue(case.name) {
                val transport = InitializeTransport(case.onInitialize)
                case.verify(shouldThrowAny { newClient().connect(transport) })
                transport.closed shouldBe true
            }
        }
    }

    @Test
    fun `should respect server capabilities`() = runTest {
        val capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(),
            tools = ServerCapabilities.Tools(),
        )
        val client = newClient()
        connect(client, newServer(capabilities))

        client.serverCapabilities shouldBe capabilities
        client.listResources()
        client.listTools()
        shouldThrow<IllegalStateException> { client.listPrompts() }.message shouldBe
            "Server does not support prompts (required for PromptsList)"
    }

    @Test
    fun `should respect client notification capabilities`() = runTest {
        val server = newServer()
        val supported = newClient(ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = true)))
        val unsupported = newClient()
        connect(supported, server)
        connect(unsupported, server)

        supported.sendRootsListChanged()
        shouldThrow<IllegalStateException> { unsupported.sendRootsListChanged() }.message shouldBe
            "Client does not support roots list changed notifications (required for NotificationsRootsListChanged)"
    }

    @Test
    fun `should respect server notification capabilities`() = runTest {
        val capabilities = ServerCapabilities(
            logging = EmptyJsonObject,
            resources = ServerCapabilities.Resources(listChanged = true),
        )
        val session = connect(newClient(), newServer(capabilities))

        session.sendLoggingMessage(
            LoggingMessageNotification(LoggingMessageNotificationParams(LoggingLevel.Info, JsonPrimitive("log"))),
        )
        session.sendResourceListChanged()
        shouldThrow<IllegalStateException> { session.sendToolListChanged() }.message shouldBe
            "Server does not support notifying of tool list changes (required for notifications/tools/list_changed)"
    }

    @Test
    fun `should only allow setRequestHandler for declared capabilities`() {
        val client = newClient(ClientCapabilities(sampling = ClientCapabilities.sampling))

        client.setRequestHandler<CreateMessageRequest>(Method.Defined.SamplingCreateMessage) { _, _ ->
            CreateMessageResult(role = Role.Assistant, content = TextContent(text = "Test response"), model = "m")
        }
        shouldThrow<IllegalStateException> {
            client.setRequestHandler<ListRootsRequest>(Method.Defined.RootsList) { _, _ -> null }
        }.message shouldBe "Client does not support roots capability (required for RootsList)"
    }

    @Test
    fun `listRoots should reflect added and removed roots`() = runTest {
        val client = newClient(ClientCapabilities(roots = ClientCapabilities.Roots()))
        val session = connect(client)
        val first = Root(uri = "file:///first", name = "first")
        val second = Root(uri = "file:///second", name = "second")

        client.addRoot(uri = "file:///first", name = "first")
        client.addRoots(listOf(second))
        session.listRoots().roots shouldContainExactlyInAnyOrder listOf(first, second)

        client.removeRoot(first.uri) shouldBe true
        client.removeRoot(first.uri) shouldBe false
        client.removeRoots(listOf(first.uri, second.uri)) shouldBe 1
        session.listRoots().roots.shouldBeEmpty()
    }

    @Test
    fun `roots mutators should require roots capability`() {
        val client = newClient()
        val mutators = mapOf<String, Client.() -> Unit>(
            "addRoot" to { addRoot(uri = "file:///root", name = "root") },
            "addRoots" to { addRoots(listOf(Root(uri = "file:///root", name = "root"))) },
            "removeRoot" to { removeRoot("file:///root") },
            "removeRoots" to { removeRoots(listOf("file:///root")) },
        )

        for ((name, mutate) in mutators) {
            withClue(name) {
                shouldThrow<IllegalStateException> { client.mutate() }.message shouldBe
                    "Client does not support roots capability."
            }
        }
    }

    @Test
    fun `sendRootsListChanged should notify server`() = runTest {
        val client = newClient(ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = true)))
        val session = connect(client)
        val received = CompletableDeferred<Unit>()
        session.setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) {
            received.complete(Unit)
            CompletableDeferred(Unit)
        }

        client.sendRootsListChanged()

        received.await()
    }

    @Test
    fun `should reject server elicitation when elicitation capability is not supported`() = runTest {
        val session = connect(newClient())

        shouldThrow<IllegalStateException> {
            session.createElicitation(message = "Provide your name", requestedSchema = nameSchema(required = true))
        }.message shouldBe "Client does not support elicitation (required for elicitation/create)"
    }

    @Test
    fun `should handle server elicitation`() = runTest {
        val schema = nameSchema(required = true)
        val accepted = ElicitResult(ElicitResult.Action.Accept, content = buildJsonObject { put("name", "octocat") })
        val session = elicitationSession { request ->
            request.params.message shouldBe "Provide your name"
            request.params.shouldBeInstanceOf<ElicitRequestFormParams>().requestedSchema shouldBe schema
            accepted
        }

        session.createElicitation(message = "Provide your name", requestedSchema = schema) shouldBe accepted
    }

    @Test
    fun `should reject accepted elicitation content that does not match requested schema`() = runTest {
        val session = elicitationSession {
            ElicitResult(action = ElicitResult.Action.Accept, content = buildJsonObject { put("name", 42) })
        }

        val exception = shouldThrow<McpException> {
            session.createElicitation(message = "mismatch", requestedSchema = nameSchema(required = true))
        }

        exception.message shouldStartWith "Elicitation response content does not match requested schema: "
        exception.message shouldContain "'name' must be string"
    }

    @Test
    fun `should reject accepted elicitation without content when schema has required properties`() = runTest {
        val session = elicitationSession { ElicitResult(action = ElicitResult.Action.Accept) }

        shouldThrow<McpException> {
            session.createElicitation(message = "no content", requestedSchema = nameSchema(required = true))
        }.message shouldContain "must have required property 'name'"
    }

    @Test
    fun `should accept elicitation without content when schema has no required properties`() = runTest {
        val session = elicitationSession { ElicitResult(action = ElicitResult.Action.Accept) }

        session.createElicitation(message = "no required", requestedSchema = nameSchema(required = false)) shouldBe
            ElicitResult(action = ElicitResult.Action.Accept)
    }

    @Test
    fun `should pass through declined elicitation without validation`() = runTest {
        val session = elicitationSession { ElicitResult(action = ElicitResult.Action.Decline) }

        session.createElicitation(message = "decline", requestedSchema = nameSchema(required = true)) shouldBe
            ElicitResult(action = ElicitResult.Action.Decline)
    }

    @Test
    fun `should apply schema defaults only to missing fields of accepted content`() = runTest {
        val session = elicitationSession {
            ElicitResult(action = ElicitResult.Action.Accept, content = buildJsonObject { put("nickname", "Custom") })
        }

        val result = session.createElicitation(message = "defaults", requestedSchema = defaultsSchema())

        result.content shouldBe buildJsonObject {
            put("nickname", "Custom")
            put("name", "John Doe")
            put("age", 30)
            put("score", 95.5)
            put("status", "active")
            put("verified", true)
            putJsonArray("tags") {
                add("a")
                add("b")
            }
            putJsonArray("options") { add("x") }
        }
    }

    @Test
    fun `should handle URL mode elicitation end-to-end`() = runTest {
        val session = elicitationSession(ClientCapabilities.Elicitation(url = EmptyJsonObject)) { request ->
            val params = request.params.shouldBeInstanceOf<ElicitRequestURLParams>()
            params.elicitationId shouldBe "550e8400-e29b-41d4-a716-446655440000"
            params.url shouldBe "https://oauth.example.com/authorize"
            ElicitResult(action = ElicitResult.Action.Accept)
        }

        session.createElicitation(
            message = "Authorize access to continue",
            elicitationId = "550e8400-e29b-41d4-a716-446655440000",
            url = "https://oauth.example.com/authorize",
        ) shouldBe ElicitResult(action = ElicitResult.Action.Accept)
    }

    @Test
    fun `should reject URL mode elicitation when client supports only form mode`() = runTest {
        val session = connect(newClient(ClientCapabilities(elicitation = ClientCapabilities.Elicitation())))

        shouldThrow<IllegalArgumentException> {
            session.createElicitation(message = "Authorize", elicitationId = "id-1", url = "https://example.com/auth")
        }.message shouldBe "Client did not advertise elicitation.url capability; cannot send a URL-mode elicitation."
    }

    @Test
    fun `should deliver elicitation complete notification to client`() = runTest {
        val received = CompletableDeferred<ElicitationCompleteNotification>()
        val client = newClient(ClientCapabilities(elicitation = ClientCapabilities.Elicitation(url = EmptyJsonObject)))
        client.setElicitationCompleteHandler { received.complete(it) }
        val session = connect(client)

        session.sendElicitationComplete(elicitationComplete("complete-id-1"))

        received.await().params.elicitationId shouldBe "complete-id-1"
    }

    @Test
    fun `should reject elicitation complete when client supports only form mode`() = runTest {
        val session = connect(newClient(ClientCapabilities(elicitation = ClientCapabilities.Elicitation())))

        shouldThrow<IllegalArgumentException> {
            session.sendElicitationComplete(elicitationComplete("id-1"))
        }.message shouldBe
            "Client did not advertise elicitation.url capability; cannot send an elicitation completion notification."
    }

    @Test
    fun `setElicitationCompleteHandler should require url capability`() {
        val client = newClient(ClientCapabilities(elicitation = ClientCapabilities.Elicitation()))

        shouldThrow<IllegalStateException> { client.setElicitationCompleteHandler { } }.message shouldBe
            "Client does not support url-mode elicitation."
    }

    @Test
    fun `should surface URL elicitation required error to client as typed exception`() = runTest {
        val required = ElicitRequestURLParams(
            message = "Authorize to continue",
            elicitationId = "auth-required-1",
            url = "https://oauth.example.com/authorize",
        )
        val server = newServer(ServerCapabilities(tools = ServerCapabilities.Tools()))
        server.addTool("needs-auth", "Requires URL elicitation") {
            throw UrlElicitationRequiredException(listOf(required))
        }
        val client = newClient(ClientCapabilities(elicitation = ClientCapabilities.Elicitation(url = EmptyJsonObject)))
        connect(client, server)

        val exception = shouldThrow<UrlElicitationRequiredException> {
            client.callTool(name = "needs-auth", arguments = emptyMap())
        }

        exception.elicitations.single() shouldBe required
    }

    private class ConnectFailure(
        val name: String,
        val onInitialize: (JSONRPCRequest) -> InitializeResult,
        val verify: (Throwable) -> Unit,
    )

    /** Answers the client's `initialize` request with [onInitialize]; a throwing [onInitialize] fails the send. */
    private class InitializeTransport(private val onInitialize: (JSONRPCRequest) -> InitializeResult) :
        AbstractTransport() {
        var closed = false

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (message is JSONRPCRequest) _onMessage(JSONRPCResponse(message.id, onInitialize(message)))
        }

        override suspend fun close() {
            closed = true
        }
    }

    private fun initializeResult(protocolVersion: String) = InitializeResult(
        protocolVersion = protocolVersion,
        capabilities = ServerCapabilities(),
        serverInfo = Implementation(name = "test server", version = "1.0"),
    )

    private fun newClient(capabilities: ClientCapabilities = ClientCapabilities()) =
        Client(Implementation(name = "test client", version = "1.0"), ClientOptions(capabilities = capabilities))

    private fun newServer(capabilities: ServerCapabilities = ServerCapabilities()) =
        Server(Implementation(name = "test server", version = "1.0"), ServerOptions(capabilities = capabilities))

    private suspend fun connect(client: Client, server: Server = newServer()): ServerSession {
        val (clientTransport, serverTransport) = InMemoryTransport.createLinkedPair()
        val session = server.createSession(serverTransport)
        client.connect(clientTransport)
        return session
    }

    private suspend fun elicitationSession(
        elicitation: ClientCapabilities.Elicitation = ClientCapabilities.Elicitation(),
        handler: (ElicitRequest) -> ElicitResult,
    ): ServerSession {
        val client = newClient(ClientCapabilities(elicitation = elicitation))
        client.setElicitationHandler(handler)
        return connect(client)
    }

    private fun elicitationComplete(elicitationId: String) =
        ElicitationCompleteNotification(ElicitationCompleteNotificationParams(elicitationId = elicitationId))

    private fun nameSchema(required: Boolean) = ElicitRequestParams.RequestedSchema(
        properties = mapOf("name" to StringSchema()),
        required = if (required) listOf("name") else null,
    )

    private fun defaultsSchema() = ElicitRequestParams.RequestedSchema(
        properties = mapOf(
            "name" to StringSchema(default = "John Doe"),
            "nickname" to StringSchema(default = "JD"),
            "age" to IntegerSchema(default = 30),
            "score" to DoubleSchema(default = 95.5),
            "status" to UntitledSingleSelectEnumSchema(enumValues = listOf("active", "inactive"), default = "active"),
            "verified" to BooleanSchema(default = true),
            "tags" to UntitledMultiSelectEnumSchema(
                items = UntitledMultiSelectEnumSchema.Items(enumValues = listOf("a", "b", "c")),
                default = listOf("a", "b"),
            ),
            "options" to TitledMultiSelectEnumSchema(
                items = TitledMultiSelectEnumSchema.Items(anyOf = listOf(EnumOption("x", "X"), EnumOption("y", "Y"))),
                default = listOf("x"),
            ),
            "comment" to StringSchema(),
        ),
    )
}
