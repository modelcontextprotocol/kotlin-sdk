package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolVersionsTest {

    @Test
    fun `every revision should belong to exactly one lifecycle`() {
        (HANDSHAKE_PROTOCOL_VERSIONS + MODERN_PROTOCOL_VERSIONS) shouldContainExactly KNOWN_PROTOCOL_VERSIONS
        HANDSHAKE_PROTOCOL_VERSIONS.intersect(MODERN_PROTOCOL_VERSIONS.toSet()).shouldBeEmptySet()
    }

    @Test
    fun `each latest constant should be the newest of its lifecycle`() {
        LATEST_PROTOCOL_VERSION shouldBe KNOWN_PROTOCOL_VERSIONS.last()
        LATEST_HANDSHAKE_VERSION shouldBe HANDSHAKE_PROTOCOL_VERSIONS.last()
        LATEST_MODERN_VERSION shouldBe MODERN_PROTOCOL_VERSIONS.last()
    }

    @Test
    fun `the handshake default should stay reachable through the handshake`() {
        assertTrue(DEFAULT_NEGOTIATED_PROTOCOL_VERSION in HANDSHAKE_PROTOCOL_VERSIONS)
    }

    @Test
    fun `the deprecated union should still name every known revision`() {
        @Suppress("DEPRECATION")
        SUPPORTED_PROTOCOL_VERSIONS.toSet() shouldBe KNOWN_PROTOCOL_VERSIONS.toSet()
    }

    @Test
    fun `should order revisions by release rather than alphabetically`() {
        KNOWN_PROTOCOL_VERSIONS.forEachIndexed { index, version ->
            KNOWN_PROTOCOL_VERSIONS.take(index + 1).forEach { older ->
                assertTrue(
                    isVersionAtLeast(version, older),
                    "$version should be at least as new as $older",
                )
            }
            KNOWN_PROTOCOL_VERSIONS.drop(index + 1).forEach { newer ->
                assertFalse(
                    isVersionAtLeast(version, newer),
                    "$version should not be as new as $newer",
                )
            }
        }
    }

    @Test
    fun `an unrecognized revision should compare as older than anything known`() {
        // Comparing as strings would put "zzz" and "9999-01-01" ahead of every real revision, which
        // is how a peer sending nonsense would talk its way into features it cannot speak.
        listOf("zzz", "9999-01-01", "", "2026-07-29").forEach { unknown ->
            assertFalse(isVersionAtLeast(unknown, KNOWN_PROTOCOL_VERSIONS.first()))
            assertFalse(isVersionAtLeast(unknown, LATEST_PROTOCOL_VERSION))
        }
    }

    @Test
    fun `should refuse to compare against a revision it does not know`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            isVersionAtLeast(LATEST_PROTOCOL_VERSION, "2027-01-01")
        }

        assertTrue(failure.message.orEmpty().contains("2027-01-01"))
    }

    @Test
    fun `only the request-scoped revisions should read as modern`() {
        KNOWN_PROTOCOL_VERSIONS.forEach { version ->
            isModernProtocolVersion(version) shouldBe (version in MODERN_PROTOCOL_VERSIONS)
        }
        assertFalse(isModernProtocolVersion("2027-01-01"))
    }

    private fun Set<String>.shouldBeEmptySet() = this shouldBe emptySet()
}
