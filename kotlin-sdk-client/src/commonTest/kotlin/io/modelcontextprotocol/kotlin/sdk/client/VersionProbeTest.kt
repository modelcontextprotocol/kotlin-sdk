// ABOUTME: Client.connect under VersionNegotiationMode.Auto against scripted servers, one probe answer per test.
// ABOUTME: Pins which server/discover answers fall back to initialize and which must fail the connection instead.
package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class VersionProbeTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** Request methods the scripted server received, in order. */
    private val received = mutableListOf<String>()

    private fun initializeResponse(id: RequestId): String = McpJson.encodeToString(
        JSONRPCMessage.serializer(),
        JSONRPCResponse(
            id = id,
            result = InitializeResult(
                protocolVersion = LATEST_HANDSHAKE_VERSION,
                capabilities = ServerCapabilities(),
                serverInfo = Implementation("legacy-server", "1.0.0"),
            ),
        ),
    )

    /** A Streamable HTTP server answering the probe with [onDiscover] and `initialize` with a handshake result. */
    private fun httpTransport(
        onDiscover: MockRequestHandleScope.() -> HttpResponseData,
    ): StreamableHttpClientTransport {
        val engine = MockEngine { data ->
            if (data.method != HttpMethod.Post) return@MockEngine respond("", HttpStatusCode.MethodNotAllowed)
            val message = McpJson.decodeFromString<JSONRPCMessage>((data.body as TextContent).text)
            if (message !is JSONRPCRequest) return@MockEngine respond("", HttpStatusCode.Accepted)
            received += message.method
            when (message.method) {
                "server/discover" -> onDiscover()
                "initialize" -> respond(initializeResponse(message.id), HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.Accepted)
            }
        }
        return StreamableHttpClientTransport(HttpClient(engine) { install(SSE) }, "http://localhost/mcp")
    }

    private fun rejectWith(status: HttpStatusCode, body: String): MockRequestHandleScope.() -> HttpResponseData =
        { respond(body, status, jsonHeaders) }

    // Protocol timeouts use delay(); runTest would skip them before the scripted server replies.
    private suspend fun connectAuto(transport: Transport): Client = withContext(Dispatchers.Default) {
        Client(
            Implementation("test-client", "1.0.0"),
            ClientOptions(versionNegotiation = VersionNegotiationMode.Auto),
        ).also { it.connect(transport) }
    }

    private suspend fun connectAutoFailure(transport: Transport): McpException = withContext(Dispatchers.Default) {
        shouldThrow<McpException> { connectAuto(transport) }
    }

    @Test
    fun `should fall back when a legacy server rejects the probe as uninitialized`() = runTest {
        // Byte for byte what a session-ful 2025-11-25 server answers before initialize.
        val transport = httpTransport(
            rejectWith(
                HttpStatusCode.BadRequest,
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32000,"message":"Bad Request: Server not initialized"}}""",
            ),
        )

        val client = connectAuto(transport)

        client.protocolVersion shouldBe LATEST_HANDSHAKE_VERSION
        received shouldBe listOf("server/discover", "initialize")
        client.close()
    }

    @Test
    fun `should fall back when the probe is rejected with an empty 4xx`() = runTest {
        val client = connectAuto(httpTransport(rejectWith(HttpStatusCode.NotFound, "")))

        client.protocolVersion shouldBe LATEST_HANDSHAKE_VERSION
        received shouldBe listOf("server/discover", "initialize")
        client.close()
    }

    @Test
    fun `should not fall back when a 400 carries an unsupported-version error`() = runTest {
        val transport = httpTransport(
            rejectWith(
                HttpStatusCode.BadRequest,
                """{"jsonrpc":"2.0","id":1,"error":{"code":-32022,"message":"Unsupported protocol version",""" +
                    """"data":{"supported":["2099-01-01"],"requested":"2026-07-28"}}}""",
            ),
        )

        val failure = connectAutoFailure(transport)

        failure.code shouldBe RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION
        received shouldBe listOf("server/discover")
    }

    @Test
    fun `should fail rather than fall back when the probe meets a server error`() = runTest {
        val failure = connectAutoFailure(httpTransport(rejectWith(HttpStatusCode.ServiceUnavailable, "")))

        failure.cause.shouldBeInstanceOf<StreamableHttpError>().code shouldBe HttpStatusCode.ServiceUnavailable.value
        received shouldBe listOf("server/discover")
    }

    @Test
    fun `should fail rather than fall back when the probe cannot reach the server`() = runTest {
        val failure = connectAutoFailure(httpTransport { throw IOException("Connection refused") })

        failure.cause.shouldBeInstanceOf<IOException>()
        received shouldBe listOf("server/discover")
    }

    @Test
    fun `should not fall back when the probe is refused with a missing-capability error`() = runTest {
        // An in-band refusal, as a stdio server sends it.
        val transport = InBandTransport(
            discoverError = RPCError(
                code = RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY,
                message = "Missing required client capability",
            ),
        )

        val failure = connectAutoFailure(transport)

        failure.code shouldBe RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY
        received shouldBe listOf("server/discover")
    }

    /** A server that answers the probe in-band with [discoverError] and `initialize` with a handshake result. */
    private inner class InBandTransport(private val discoverError: RPCError) : Transport {
        private var onMessageBlock: (suspend (JSONRPCMessage) -> Unit)? = null
        private var onCloseBlock: (() -> Unit)? = null

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (message !is JSONRPCRequest) return
            received += message.method
            val reply = when (message.method) {
                "server/discover" -> JSONRPCError(id = message.id, error = discoverError)
                "initialize" -> McpJson.decodeFromString<JSONRPCMessage>(initializeResponse(message.id))
                else -> return
            }
            onMessageBlock?.invoke(reply)
        }

        override suspend fun close() {
            onCloseBlock?.invoke()
        }

        override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
            onMessageBlock = block
        }

        override fun onClose(block: () -> Unit) {
            onCloseBlock = block
        }

        override fun onError(block: (Throwable) -> Unit) = Unit
    }
}
