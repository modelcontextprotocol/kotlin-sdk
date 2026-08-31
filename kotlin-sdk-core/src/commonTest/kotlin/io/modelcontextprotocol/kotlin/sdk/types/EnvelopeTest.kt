package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalMcpApi::class)
class EnvelopeTest {

    private val capabilities = ClientCapabilities(sampling = ClientCapabilities.Sampling())
    private val clientInfo = Implementation(name = "envelope-client", version = "1.0.0")

    private fun metaOf(json: String) = RequestMeta(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun `should render every envelope field into request metadata`() {
        val meta = RequestEnvelope(
            protocolVersion = "2026-07-28",
            clientCapabilities = capabilities,
            clientInfo = clientInfo,
            logLevel = LoggingLevel.Warning,
        ).toMeta()

        McpJson.encodeToString(meta) shouldEqualJson """
            {
              "io.modelcontextprotocol/protocolVersion": "2026-07-28",
              "io.modelcontextprotocol/clientCapabilities": {"sampling": {}},
              "io.modelcontextprotocol/clientInfo": {
                "name": "envelope-client",
                "version": "1.0.0"
              },
              "io.modelcontextprotocol/logLevel": "warning"
            }
        """.trimIndent()
    }

    @Test
    fun `should omit absent optional envelope fields`() {
        val meta = RequestEnvelope("2026-07-28", ClientCapabilities()).toMeta()

        assertNull(meta[CLIENT_INFO_META_KEY])
        assertNull(meta[LOG_LEVEL_META_KEY])
        McpJson.encodeToString(meta) shouldEqualJson """
            {
              "io.modelcontextprotocol/protocolVersion": "2026-07-28",
              "io.modelcontextprotocol/clientCapabilities": {}
            }
        """.trimIndent()
    }

    @Test
    fun `should preserve unrelated base metadata entries`() {
        val base = RequestMeta(
            buildJsonObject {
                put("progressToken", "token-1")
                put("com.example/traceId", "trace-123")
            },
        )

        val meta = RequestEnvelope("2026-07-28", ClientCapabilities()).toMeta(base)

        meta.progressToken shouldBe ProgressToken("token-1")
        meta["com.example/traceId"] shouldBe base["com.example/traceId"]
        meta.claimedProtocolVersion shouldBe "2026-07-28"
    }

    @Test
    fun `should replace protocol entries already present in the base`() {
        val base = RequestEnvelope(
            protocolVersion = "2025-11-25",
            clientCapabilities = capabilities,
            clientInfo = clientInfo,
            logLevel = LoggingLevel.Debug,
        ).toMeta()

        val meta = RequestEnvelope("2026-07-28", ClientCapabilities()).toMeta(base)

        meta.toEnvelope() shouldBe RequestEnvelope("2026-07-28", ClientCapabilities())
        assertNull(meta[CLIENT_INFO_META_KEY])
        assertNull(meta[LOG_LEVEL_META_KEY])
    }

    @Test
    fun `should round trip an envelope through request metadata`() {
        val envelope = RequestEnvelope("2026-07-28", capabilities, clientInfo, LoggingLevel.Warning)

        envelope.toMeta().toEnvelope() shouldBe envelope
    }

    @Test
    fun `should read an envelope alongside unrelated metadata entries`() {
        val meta = metaOf(
            """
            {
              "progressToken": "token-1",
              "com.example/traceId": "trace-123",
              "io.modelcontextprotocol/protocolVersion": "2026-07-28",
              "io.modelcontextprotocol/clientCapabilities": {"roots": {}}
            }
            """.trimIndent(),
        )

        meta.toEnvelope() shouldBe RequestEnvelope(
            protocolVersion = "2026-07-28",
            clientCapabilities = ClientCapabilities(roots = ClientCapabilities.Roots()),
        )
    }

    @Test
    fun `empty client capabilities should mean no optional capabilities`() {
        val meta = metaOf(
            """
            {
              "io.modelcontextprotocol/protocolVersion": "2026-07-28",
              "io.modelcontextprotocol/clientCapabilities": {}
            }
            """.trimIndent(),
        )

        meta.toEnvelope().clientCapabilities shouldBe ClientCapabilities()
    }

    @Test
    fun `should read no envelope from metadata that predates the request scoped lifecycle`() {
        val meta = metaOf("""{"progressToken": "token-1"}""")

        assertNull(meta.toEnvelopeOrNull())
        assertNull(meta.claimedProtocolVersion)
    }

    @Test
    fun `should report the offending key when a required envelope field is absent`() {
        val required = listOf(
            """{"io.modelcontextprotocol/clientCapabilities": {}}""" to PROTOCOL_VERSION_META_KEY,
            """{"io.modelcontextprotocol/protocolVersion": "2026-07-28"}""" to CLIENT_CAPABILITIES_META_KEY,
        )

        required.forEach { (json, missingKey) ->
            val meta = metaOf(json)

            assertNull(meta.toEnvelopeOrNull())
            val failure = assertFailsWith<SerializationException> { meta.toEnvelope() }
            assertTrue(
                failure.message.orEmpty().contains(missingKey),
                "Expected failure to name $missingKey, but was: $failure",
            )
        }
    }

    @Test
    fun `should report the offending key when an envelope field has the wrong json kind`() {
        val illTyped = listOf(
            PROTOCOL_VERSION_META_KEY to "{}",
            PROTOCOL_VERSION_META_KEY to "null",
            CLIENT_INFO_META_KEY to "\"not-an-implementation\"",
            CLIENT_CAPABILITIES_META_KEY to "\"not-capabilities\"",
            CLIENT_CAPABILITIES_META_KEY to "[]",
            CLIENT_CAPABILITIES_META_KEY to "null",
        )

        illTyped.forEach { (key, value) ->
            val meta = envelopeMetaWith(key, value)

            assertNull(meta.toEnvelopeOrNull())
            val failure = assertFailsWith<SerializationException> { meta.toEnvelope() }
            assertTrue(
                failure.message.orEmpty().contains(key),
                "Expected failure to name $key, but was: $failure",
            )
        }
    }

    @Test
    fun `should report what a well typed but out of domain envelope value violates`() {
        val outOfDomain = listOf(
            Triple(CLIENT_INFO_META_KEY, """{"name":"missing-version"}""", "version"),
            Triple(LOG_LEVEL_META_KEY, "\"verbose\"", "verbose"),
            Triple(LOG_LEVEL_META_KEY, "7", "7"),
        )

        outOfDomain.forEach { (key, value, expected) ->
            val meta = envelopeMetaWith(key, value)

            assertNull(meta.toEnvelopeOrNull())
            val failure = assertFailsWith<SerializationException> { meta.toEnvelope() }
            assertTrue(
                failure.message.orEmpty().contains(expected),
                "Expected failure to name $expected, but was: $failure",
            )
        }
    }

    private fun envelopeMetaWith(key: String, value: String) = metaOf(
        """
        {
          "io.modelcontextprotocol/protocolVersion": "2026-07-28",
          "io.modelcontextprotocol/clientCapabilities": {},
          "$key": $value
        }
        """.trimIndent(),
    )

    @Test
    fun `should read a claimed protocol version without validating the rest of the envelope`() {
        val meta = metaOf("""{"io.modelcontextprotocol/protocolVersion": "2026-07-28"}""")

        meta.claimedProtocolVersion shouldBe "2026-07-28"
        assertNull(meta.toEnvelopeOrNull())
    }

    @Test
    fun `should read no claimed protocol version from a non string claim`() {
        assertNull(metaOf("""{"io.modelcontextprotocol/protocolVersion": {}}""").claimedProtocolVersion)
        assertNull(metaOf("""{"io.modelcontextprotocol/protocolVersion": 2026}""").claimedProtocolVersion)
    }

    @Test
    fun `should read server info from result metadata`() {
        val meta = ResultMeta(
            Json.parseToJsonElement(
                """
                {
                  "io.modelcontextprotocol/serverInfo": {"name": "test-server", "version": "2.0.0"},
                  "com.example/source": "edge"
                }
                """.trimIndent(),
            ) as JsonObject,
        )

        meta.serverInfo shouldBe Implementation(name = "test-server", version = "2.0.0")
    }

    @Test
    fun `should read no server info when the result carries none`() {
        assertNull(ResultMeta(buildJsonObject { put("com.example/source", "edge") }).serverInfo)
    }

    @Test
    fun `should reject malformed server info`() {
        val meta = ResultMeta(buildJsonObject { put(SERVER_INFO_META_KEY, "not-an-implementation") })

        assertFailsWith<SerializationException> { meta.serverInfo }
    }

    @Test
    fun `should write server info preserving unrelated result metadata`() {
        val base = ResultMeta(buildJsonObject { put("com.example/source", "edge") })

        val meta = base.withServerInfo(Implementation(name = "test-server", version = "2.0.0"))

        meta.serverInfo shouldBe Implementation(name = "test-server", version = "2.0.0")
        meta["com.example/source"] shouldBe base["com.example/source"]
    }

    @Test
    fun `should replace a server info already present`() {
        val base = ResultMeta(buildJsonObject { put("com.example/source", "edge") })
            .withServerInfo(Implementation(name = "old", version = "1.0.0"))

        val meta = base.withServerInfo(Implementation(name = "new", version = "2.0.0"))

        meta.serverInfo shouldBe Implementation(name = "new", version = "2.0.0")
    }

    @Test
    fun `should write server info onto absent result metadata`() {
        val meta = null.withServerInfo(Implementation(name = "test-server", version = "2.0.0"))

        meta.serverInfo shouldBe Implementation(name = "test-server", version = "2.0.0")
    }

    @Test
    fun `should report every required capability as missing when nothing is declared`() {
        val required = ClientCapabilities(
            sampling = ClientCapabilities.Sampling(),
            elicitation = ClientCapabilities.Elicitation(url = JsonObject(emptyMap())),
        )

        required.missingIn(null) shouldBe required
        required.missingIn(ClientCapabilities()) shouldBe required
    }

    @Test
    fun `should report nothing missing when every required capability is declared`() {
        val required = ClientCapabilities(sampling = ClientCapabilities.Sampling())
        val declared = ClientCapabilities(
            sampling = ClientCapabilities.Sampling(tools = JsonObject(emptyMap())),
            roots = ClientCapabilities.Roots(),
        )

        assertNull(required.missingIn(declared))
    }

    @Test
    fun `should narrow a partially declared capability to its missing sub capabilities`() {
        val required = ClientCapabilities(
            sampling = ClientCapabilities.Sampling(
                context = JsonObject(emptyMap()),
                tools = JsonObject(emptyMap()),
            ),
        )
        val declared = ClientCapabilities(
            sampling = ClientCapabilities.Sampling(context = JsonObject(emptyMap())),
        )

        required.missingIn(declared) shouldBe ClientCapabilities(
            sampling = ClientCapabilities.Sampling(tools = JsonObject(emptyMap())),
        )
    }

    @Test
    fun `a bare elicitation declaration should satisfy a form mode requirement`() {
        val required = ClientCapabilities(elicitation = ClientCapabilities.Elicitation(form = JsonObject(emptyMap())))

        assertNull(required.missingIn(ClientCapabilities(elicitation = ClientCapabilities.Elicitation())))
    }

    @Test
    fun `a declaration naming a mode should not imply the other mode`() {
        val required = ClientCapabilities(elicitation = ClientCapabilities.Elicitation(form = JsonObject(emptyMap())))
        val declared = ClientCapabilities(elicitation = ClientCapabilities.Elicitation(url = JsonObject(emptyMap())))

        required.missingIn(declared) shouldBe required
    }

    @Test
    fun `should compare experimental and extension capabilities by key`() {
        val required = ClientCapabilities(
            experimental = buildJsonObject { put("com.example/alpha", JsonObject(emptyMap())) },
            extensions = mapOf("com.example/beta" to JsonObject(emptyMap())),
        )
        val declared = ClientCapabilities(
            experimental = buildJsonObject { put("com.example/alpha", JsonObject(emptyMap())) },
        )

        required.missingIn(declared) shouldBe ClientCapabilities(
            extensions = mapOf("com.example/beta" to JsonObject(emptyMap())),
        )
    }

    @Test
    fun `should carry the missing capabilities in the error data`() {
        val missing = ClientCapabilities(sampling = ClientCapabilities.Sampling())

        McpJson.encodeToString(MissingRequiredClientCapabilityData(missing)) shouldEqualJson """
            {"requiredCapabilities": {"sampling": {}}}
        """.trimIndent()
    }

    @Test
    fun `an intact envelope should report no problems`() {
        val meta = RequestEnvelope("2026-07-28", capabilities, clientInfo, LoggingLevel.Warning).toMeta()

        validateEnvelope(meta) shouldBe emptyList()
    }

    @Test
    fun `a required key that is absent should be reported as missing`() {
        validateEnvelope(metaOf("{}")) shouldBe listOf(
            EnvelopeIssue(PROTOCOL_VERSION_META_KEY, "missing"),
            EnvelopeIssue(CLIENT_CAPABILITIES_META_KEY, "missing"),
        )
    }

    @Test
    fun `every missing key should be reported at once`() {
        // A peer that sent neither is told both, rather than being corrected one round trip at a time.
        val meta = metaOf("""{"progressToken": "token-1"}""")

        validateEnvelope(meta).map { it.key } shouldBe listOf(
            PROTOCOL_VERSION_META_KEY,
            CLIENT_CAPABILITIES_META_KEY,
        )
    }

    @Test
    fun `missing keys should be reported before malformed ones`() {
        val meta = metaOf("""{"io.modelcontextprotocol/clientCapabilities": "not-capabilities"}""")

        validateEnvelope(meta) shouldBe listOf(
            EnvelopeIssue(PROTOCOL_VERSION_META_KEY, "missing"),
            EnvelopeIssue(CLIENT_CAPABILITIES_META_KEY, "must be a client capabilities object"),
        )
    }

    @Test
    fun `a required key of the wrong shape should be reported`() {
        listOf("{}", "[]", "7", "null", "true").forEach { value ->
            validateEnvelope(envelopeMetaWith(PROTOCOL_VERSION_META_KEY, value)) shouldBe listOf(
                EnvelopeIssue(PROTOCOL_VERSION_META_KEY, "must be a JSON string"),
            )
        }

        listOf("\"nope\"", "[]", "7", "null", """{"sampling": 7}""").forEach { value ->
            validateEnvelope(envelopeMetaWith(CLIENT_CAPABILITIES_META_KEY, value)) shouldBe listOf(
                EnvelopeIssue(CLIENT_CAPABILITIES_META_KEY, "must be a client capabilities object"),
            )
        }
    }

    @Test
    fun `an unknown protocol version should not be an envelope problem`() {
        // Which versions are acceptable is a negotiation outcome answered with its own error code;
        // the envelope is only asked whether it is shaped like an envelope.
        validateEnvelope(envelopeMetaWith(PROTOCOL_VERSION_META_KEY, "\"1999-01-01\"")) shouldBe emptyList()
    }

    @Test
    fun `a malformed client identity should never block a request`() {
        // Identity is self-reported, unverified and for display only, and the revision says a
        // receiver must not change its behaviour on account of it. Refusing to serve would be
        // exactly that, so it reads as absent instead.
        listOf(
            "\"not-an-implementation\"",
            """{"name": "missing-version"}""",
            "7",
            "null",
        ).forEach { value ->
            validateEnvelope(envelopeMetaWith(CLIENT_INFO_META_KEY, value)) shouldBe emptyList()
        }
    }

    @Test
    fun `a malformed log level should be an envelope problem`() {
        // The revision has a server SHOULD answer -32602 for a level it does not recognize rather
        // than guess: guessing either leaks messages the client never asked for, or drops ones it did.
        listOf(
            "\"verbose\"",
            "\"warn\"",
            "7",
            "{}",
        ).forEach { value ->
            validateEnvelope(envelopeMetaWith(LOG_LEVEL_META_KEY, value)) shouldBe
                listOf(EnvelopeIssue(LOG_LEVEL_META_KEY, "must be a known logging level"))
        }
    }

    @Test
    fun `a known log level should not be an envelope problem`() {
        validateEnvelope(envelopeMetaWith(LOG_LEVEL_META_KEY, "\"warning\"")) shouldBe emptyList()
    }

    @Test
    fun `the lenient reader should admit exactly what validation admits`() {
        // The gate and the reader a receiver serves with have to agree: a request admitted by
        // validateEnvelope must never then be served without the envelope it was admitted for.
        listOf(
            CLIENT_INFO_META_KEY to "\"not-an-implementation\"",
            CLIENT_INFO_META_KEY to """{"name": "missing-version"}""",
            CLIENT_INFO_META_KEY to "7",
            LOG_LEVEL_META_KEY to "\"warning\"",
            PROTOCOL_VERSION_META_KEY to "\"1999-01-01\"",
        ).forEach { (key, value) ->
            val meta = envelopeMetaWith(key, value)

            validateEnvelope(meta) shouldBe emptyList()
            assertTrue(
                meta.toEnvelopeLenient() != null,
                "Expected an envelope for $key = $value, which validation admitted",
            )
        }
    }

    @Test
    fun `the lenient reader should degrade a malformed identity to absent`() {
        val envelope = envelopeMetaWith(CLIENT_INFO_META_KEY, """{"name": "missing-version"}""")
            .toEnvelopeLenient()

        // The required fields survive: dropping them is what silently demoted the request to the
        // connection-scoped lifecycle, taking its capabilities, version and log gating with it.
        envelope?.protocolVersion shouldBe "2026-07-28"
        envelope?.clientCapabilities shouldBe ClientCapabilities()
        assertNull(envelope?.clientInfo)
    }

    @Test
    fun `the lenient reader should refuse a missing or malformed required field`() {
        assertNull(metaOf("""{"io.modelcontextprotocol/clientCapabilities": {}}""").toEnvelopeLenient())
        assertNull(metaOf("""{"io.modelcontextprotocol/protocolVersion": "2026-07-28"}""").toEnvelopeLenient())
        assertNull(envelopeMetaWith(CLIENT_CAPABILITIES_META_KEY, "\"nope\"").toEnvelopeLenient())
        assertNull(envelopeMetaWith(PROTOCOL_VERSION_META_KEY, "7").toEnvelopeLenient())
    }
}
