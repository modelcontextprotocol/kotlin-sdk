package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.modelcontextprotocol.kotlin.sdk.InternalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.CLIENT_CAPABILITIES_META_KEY
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.MCP_METHOD_HEADER
import io.modelcontextprotocol.kotlin.sdk.types.MCP_NAME_HEADER
import io.modelcontextprotocol.kotlin.sdk.types.MCP_PROTOCOL_VERSION_HEADER
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * The inbound ladder, cell by cell, driven through the pure classifier — one test per rung
 * behavior, so a rung cannot be reordered or dropped without a named failure here.
 */
@OptIn(InternalMcpApi::class)
class InboundClassificationTest {

    private fun envelope(version: String = LATEST_MODERN_VERSION): String =
        """
        "_meta": {
          "io.modelcontextprotocol/protocolVersion": "$version",
          "io.modelcontextprotocol/clientCapabilities": {}
        }
        """.trimIndent()

    private fun request(id: String = "1", method: String = "tools/list", meta: String? = envelope()): String {
        val params = meta?.let { """, "params": {$it}""" } ?: ""
        return """{"jsonrpc": "2.0", "id": $id, "method": "$method"$params}"""
    }

    private fun modernHeaders(
        version: String = LATEST_MODERN_VERSION,
        method: String? = "tools/list",
        name: String? = null,
    ): Headers {
        val pairs = buildList {
            add(MCP_PROTOCOL_VERSION_HEADER to listOf(version))
            method?.let { add(MCP_METHOD_HEADER to listOf(it)) }
            name?.let { add(MCP_NAME_HEADER to listOf(it)) }
        }
        return headersOf(*pairs.toTypedArray())
    }

    private fun classify(body: String, headers: Headers = modernHeaders()): InboundOutcome =
        classifyInboundRequest(McpJson.parseToJsonElement(body), headers)

    private fun InboundOutcome.shouldReject(code: Int, status: HttpStatusCode): InboundOutcome.Reject {
        val reject = shouldBeInstanceOf<InboundOutcome.Reject>()
        reject.error.code shouldBe code
        reject.status shouldBe status
        return reject
    }

    // Rung 1 — jsonrpc-shape.

    @Test
    fun `should refuse a batch carrying a claim`() {
        val body = """[${request()}, ${request(id = "2", meta = null)}]"""
        classify(body).shouldReject(RPCError.ErrorCode.INVALID_REQUEST, HttpStatusCode.BadRequest)
    }

    @Test
    fun `should keep an all-handshake batch as legacy traffic`() {
        val body = """[${request(meta = null)}, ${request(id = "2", meta = null)}]"""
        classify(body, headers = headersOf()).shouldBeInstanceOf<InboundOutcome.Legacy>()
    }

    @Test
    fun `should refuse a body that is not an object or an array`() {
        classify("42").shouldReject(RPCError.ErrorCode.INVALID_REQUEST, HttpStatusCode.BadRequest)
    }

    @Test
    fun `should refuse an id that is not a string or an integer`() {
        // Each shape previously escaped the classifier as a serializer exception, i.e. an HTTP 500.
        for (id in listOf("null", "true", "1.5", "{}", "[]")) {
            val reject = classify(request(id = id))
                .shouldReject(RPCError.ErrorCode.INVALID_REQUEST, HttpStatusCode.BadRequest)
            assertNull(reject.id, "an unusable id cannot be echoed")
        }
    }

    // Rung 2 — standard headers, requests only.

    @Test
    fun `should classify an intact enveloped request as modern`() {
        val outcome = classify(request()).shouldBeInstanceOf<InboundOutcome.Modern>()
        outcome.method shouldBe "tools/list"
        outcome.id shouldBe RequestId(1L)
        outcome.protocolVersion shouldBe LATEST_MODERN_VERSION
    }

    @Test
    fun `should report a self-disagreement ahead of an unsupported version`() {
        // Spec precedence: a client disagreeing with itself is told so, not told its version is
        // unsupported — the envelope here names a revision the server would also refuse.
        val body = request(meta = envelope(version = LATEST_HANDSHAKE_VERSION))
        classify(body, modernHeaders(version = LATEST_MODERN_VERSION))
            .shouldReject(RPCError.ErrorCode.HEADER_MISMATCH, HttpStatusCode.BadRequest)
    }

    @Test
    fun `should refuse a modern request without the version header`() {
        classify(request(), headersOf(MCP_METHOD_HEADER, "tools/list"))
            .shouldReject(RPCError.ErrorCode.HEADER_MISMATCH, HttpStatusCode.BadRequest)
    }

    @Test
    fun `should refuse a modern request without the method header`() {
        classify(request(), modernHeaders(method = null))
            .shouldReject(RPCError.ErrorCode.HEADER_MISMATCH, HttpStatusCode.BadRequest)
    }

    @Test
    fun `should refuse a name header disagreeing with the body`() {
        val body = """
            {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
             "params": {"name": "add", ${envelope()}}}
        """.trimIndent()
        classify(body, modernHeaders(method = "tools/call", name = "subtract"))
            .shouldReject(RPCError.ErrorCode.HEADER_MISMATCH, HttpStatusCode.BadRequest)
    }

    // Rung 3 — envelope.

    @Test
    fun `should refuse a claim whose envelope is missing a required key`() {
        val meta = """"_meta": {"io.modelcontextprotocol/protocolVersion": "$LATEST_MODERN_VERSION"}"""
        val reject = classify(request(meta = meta))
            .shouldReject(RPCError.ErrorCode.INVALID_PARAMS, HttpStatusCode.BadRequest)
        reject.id shouldBe RequestId(1L)
        reject.error.message shouldContain CLIENT_CAPABILITIES_META_KEY
    }

    @Test
    fun `should refuse a claim-free request under a modern version header naming the missing keys`() {
        val reject = classify(request(meta = null))
            .shouldReject(RPCError.ErrorCode.INVALID_PARAMS, HttpStatusCode.BadRequest)
        reject.id shouldBe RequestId(1L)
    }

    @Test
    fun `should keep a claim-free request without a modern header as legacy traffic`() {
        classify(request(meta = null), headers = headersOf()).shouldBeInstanceOf<InboundOutcome.Legacy>()
    }

    // Rung 4 — version.

    @Test
    fun `should refuse an envelope naming a handshake revision as unsupported`() {
        val body = request(meta = envelope(version = LATEST_HANDSHAKE_VERSION))
        val reject = classify(body, modernHeaders(version = LATEST_HANDSHAKE_VERSION))
            .shouldReject(RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION, HttpStatusCode.BadRequest)
        val data = McpJson.decodeFromJsonElement<UnsupportedProtocolVersionData>(reject.error.data!!)
        data.requested shouldBe LATEST_HANDSHAKE_VERSION
        data.supported shouldBe listOf(LATEST_MODERN_VERSION)
    }

    // Notifications — id-less bodies are routed by the version header alone, as in the reference
    // SDKs; the spec answers them 202 on this wire.

    @Test
    fun `should acknowledge a claim-free notification under a modern version header`() {
        val outcome = classify(
            """{"jsonrpc": "2.0", "method": "notifications/cancelled"}""",
            modernHeaders(method = null),
        ).shouldBeInstanceOf<InboundOutcome.ModernNotification>()
        outcome.method shouldBe "notifications/cancelled"
    }

    @Test
    fun `should route a notification by the header even when its body carries a claim`() {
        // The claim — malformed here — is ignored: notifications carry no body claim at this
        // revision, so nothing in the body can reclassify or refuse one.
        val meta = """"_meta": {"io.modelcontextprotocol/protocolVersion": 7}"""
        val body = """{"jsonrpc": "2.0", "method": "notifications/cancelled", "params": {$meta}}"""
        classify(body, modernHeaders(method = null)).shouldBeInstanceOf<InboundOutcome.ModernNotification>()
    }

    @Test
    fun `should keep a notification without a modern header as legacy traffic`() {
        classify("""{"jsonrpc": "2.0", "method": "notifications/initialized"}""", headers = headersOf())
            .shouldBeInstanceOf<InboundOutcome.Legacy>()
    }

    @Test
    fun `should refuse a notification method header disagreeing with the body`() {
        classify(
            """{"jsonrpc": "2.0", "method": "notifications/cancelled"}""",
            modernHeaders(method = "notifications/progress"),
        ).shouldReject(RPCError.ErrorCode.HEADER_MISMATCH, HttpStatusCode.BadRequest)
    }

    @Test
    fun `should refuse an id-less body without a method`() {
        classify("""{"jsonrpc": "2.0"}""").shouldReject(RPCError.ErrorCode.INVALID_REQUEST, HttpStatusCode.BadRequest)
    }
}
