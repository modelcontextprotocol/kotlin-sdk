package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverResult
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.UnsupportedProtocolVersionData
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The probe policy, outcome by outcome, driven through the pure classifier. Each verdict decides
 * whether a connection silently downgrades, hard-fails, or re-probes, so every cell is pinned —
 * including its no-fallback ([VersionNegotiationMode.Pin]) variant where the two differ.
 */
@OptIn(ExperimentalMcpApi::class)
class VersionNegotiationTest {

    private val modernVersions = listOf(LATEST_MODERN_VERSION)

    private fun discover(vararg supported: String) = DiscoverResult(
        supportedVersions = supported.toList(),
        capabilities = ServerCapabilities(),
    )

    private fun unsupportedVersionError(vararg supported: String) = McpException(
        code = RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION,
        message = "Unsupported protocol version",
        data = McpJson.encodeToJsonElement(
            UnsupportedProtocolVersionData(supported = supported.toList(), requested = LATEST_MODERN_VERSION),
        ),
    )

    private fun classify(
        outcome: ProbeOutcome,
        fallbackAvailable: Boolean = true,
        overStdio: Boolean = false,
    ): ProbeVerdict = classifyProbeOutcome(
        outcome = outcome,
        clientModernVersions = modernVersions,
        fallbackAvailable = fallbackAvailable,
        overStdio = overStdio,
    )

    @Test
    fun `should reject pinning a handshake revision at construction pointing at legacy`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            VersionNegotiationMode.Pin(LATEST_HANDSHAKE_VERSION)
        }
        failure.message!! shouldContain "VersionNegotiationMode.Legacy"
    }

    @Test
    fun `should adopt a discovery answer sharing a modern revision`() {
        val answer = discover(LATEST_MODERN_VERSION, "2099-01-01")
        val verdict = classify(ProbeOutcome.Answered(answer)).shouldBeInstanceOf<ProbeVerdict.Modern>()
        verdict.version shouldBe LATEST_MODERN_VERSION
        verdict.discover shouldBe answer
    }

    @Test
    fun `should fall back on a discovery answer advertising only handshake revisions`() {
        val answer = ProbeOutcome.Answered(discover(LATEST_HANDSHAKE_VERSION))
        classify(answer) shouldBe ProbeVerdict.Legacy
        classify(answer, fallbackAvailable = false).shouldBeInstanceOf<ProbeVerdict.Failed>()
    }

    @Test
    fun `should fall back on an unparseable discovery answer`() {
        val answer = ProbeOutcome.Answered(result = null)
        classify(answer) shouldBe ProbeVerdict.Legacy
        classify(answer, fallbackAvailable = false).shouldBeInstanceOf<ProbeVerdict.Failed>()
    }

    @Test
    fun `should re-probe once at a mutual revision named by an unsupported-version refusal`() {
        // The corrective continuation is part of negotiation, never counted as a retry — and it
        // applies under Pin too, since the server named a revision both ends speak.
        val refusal = ProbeOutcome.Refused(unsupportedVersionError("2099-01-01", LATEST_MODERN_VERSION))
        classify(refusal) shouldBe ProbeVerdict.Corrective(LATEST_MODERN_VERSION)
        classify(refusal, fallbackAvailable = false) shouldBe ProbeVerdict.Corrective(LATEST_MODERN_VERSION)
    }

    @Test
    fun `should fail on a refusal naming only modern revisions this client does not have`() {
        val refusal = ProbeOutcome.Refused(unsupportedVersionError("2099-01-01"))
        classify(refusal).shouldBeInstanceOf<ProbeVerdict.Failed>()
            .cause.code shouldBe RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION
    }

    @Test
    fun `should fall back on a refusal naming a handshake revision`() {
        val refusal = ProbeOutcome.Refused(unsupportedVersionError("2099-01-01", LATEST_HANDSHAKE_VERSION))
        classify(refusal) shouldBe ProbeVerdict.Legacy
    }

    @Test
    fun `should fall back on an unsupported-version refusal whose data does not decode`() {
        val refusal = ProbeOutcome.Refused(
            McpException(code = RPCError.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION, message = "no data"),
        )
        classify(refusal) shouldBe ProbeVerdict.Legacy
    }

    @Test
    fun `should fall back on any other refusal code`() {
        // Denylist, not allowlist: -32601 is what a server without discovery answers.
        val refusal = ProbeOutcome.Refused(
            McpException(code = RPCError.ErrorCode.METHOD_NOT_FOUND, message = "Method not found"),
        )
        classify(refusal) shouldBe ProbeVerdict.Legacy
        classify(refusal, fallbackAvailable = false).shouldBeInstanceOf<ProbeVerdict.Failed>()
            .cause.code shouldBe RPCError.ErrorCode.METHOD_NOT_FOUND
    }

    @Test
    fun `should read stdio silence as a handshake-era signal`() {
        classify(ProbeOutcome.Silent, overStdio = true) shouldBe ProbeVerdict.Legacy
        classify(ProbeOutcome.Silent, overStdio = true, fallbackAvailable = false)
            .shouldBeInstanceOf<ProbeVerdict.Failed>()
    }

    @Test
    fun `should fail on http silence because an outage is never an era verdict`() {
        classify(ProbeOutcome.Silent, overStdio = false).shouldBeInstanceOf<ProbeVerdict.Failed>()
            .cause.code shouldBe RPCError.ErrorCode.REQUEST_TIMEOUT
    }
}
