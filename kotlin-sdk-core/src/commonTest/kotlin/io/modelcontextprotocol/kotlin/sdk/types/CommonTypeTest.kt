package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.test.utils.verifyDeserialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import io.modelcontextprotocol.kotlin.test.utils.verifySerializationRoundTrip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommonTypeTest {

    @Test
    fun `should have correct latest protocol version`() {
        LATEST_PROTOCOL_VERSION shouldBe "2025-11-25"
    }

    @Test
    fun `should have correct supported protocol versions`() {
        SUPPORTED_PROTOCOL_VERSIONS shouldContainExactlyInAnyOrder listOf(
            LATEST_PROTOCOL_VERSION,
            "2025-06-18",
            "2025-03-26",
            "2024-11-05",
        )
    }

    @Test
    fun `should serialize Icon with minimal fields`() {
        val icon = Icon(src = "https://example.com/icon.png")
        verifySerialization(
            icon,
            McpJson,
            """
            {
              "src": "https://example.com/icon.png"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize Icon with all fields`() {
        val icon = Icon(
            src = "https://example.com/icon.png",
            mimeType = "image/png",
            sizes = listOf("48x48", "96x96"),
            theme = Icon.Theme.Light,
        )
        verifySerialization(
            icon,
            McpJson,
            """
            {
              "src": "https://example.com/icon.png",
              "mimeType": "image/png",
              "sizes": ["48x48", "96x96"],
              "theme": "light"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize Icon from JSON`() {
        val json = """
            {
              "src": "https://example.com/icon.png",
              "mimeType": "image/png",
              "sizes": ["48x48"],
              "theme": "dark"
            }
        """.trimIndent()

        val icon = verifyDeserialization<Icon>(McpJson, json)

        assertEquals("https://example.com/icon.png", icon.src)
        assertEquals("image/png", icon.mimeType)
        assertEquals(listOf("48x48"), icon.sizes)
        assertEquals(Icon.Theme.Dark, icon.theme)
    }

    @Test
    fun `should deserialize Icon with minimal fields from JSON`() {
        val json = """{"src": "https://example.com/icon.png"}"""

        val icon = verifyDeserialization<Icon>(McpJson, json)

        assertEquals("https://example.com/icon.png", icon.src)
        assertNull(icon.mimeType)
        assertNull(icon.sizes)
        assertNull(icon.theme)
    }

    @Test
    fun `should serialize theme as lowercase strings`() {
        val lightIcon = Icon(src = "test.png", theme = Icon.Theme.Light)
        val darkIcon = Icon(src = "test.png", theme = Icon.Theme.Dark)

        val lightJson = McpJson.encodeToString(lightIcon)
        val darkJson = McpJson.encodeToString(darkIcon)

        assertTrue(lightJson.contains("\"theme\":\"light\""))
        assertTrue(darkJson.contains("\"theme\":\"dark\""))
    }

    @Test
    fun `should serialize Role as lowercase strings`() {
        val userJson = McpJson.encodeToString(Role.User)
        val assistantJson = McpJson.encodeToString(Role.Assistant)

        assertEquals("\"user\"", userJson)
        assertEquals("\"assistant\"", assistantJson)
    }

    @Test
    fun `should deserialize Role from lowercase strings`() {
        val user = verifyDeserialization<Role>(McpJson, "\"user\"")
        val assistant = verifyDeserialization<Role>(McpJson, "\"assistant\"")

        assertEquals(Role.User, user)
        assertEquals(Role.Assistant, assistant)
    }

    @Test
    fun `should serialize Annotations with all fields`() {
        val annotations = Annotations(
            audience = listOf(Role.User, Role.Assistant),
            priority = 0.8,
            lastModified = "2025-01-12T15:00:58Z",
        )

        verifySerialization(
            annotations,
            McpJson,
            """
            {
              "audience": ["user", "assistant"],
              "priority": 0.8,
              "lastModified": "2025-01-12T15:00:58Z"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should serialize Annotations with minimal fields`() {
        val annotations = Annotations(priority = 0.5)

        verifySerialization(
            annotations,
            McpJson,
            """
            {
              "priority": 0.5
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `should deserialize Annotations from JSON`() {
        val json = """
            {
              "audience": ["user"],
              "priority": 0.9,
              "lastModified": "2025-01-12T15:00:58Z"
            }
        """.trimIndent()

        val annotations = verifyDeserialization<Annotations>(McpJson, json)

        assertEquals(listOf(Role.User), annotations.audience)
        assertEquals(0.9, annotations.priority)
        assertEquals("2025-01-12T15:00:58Z", annotations.lastModified)
    }

    @Test
    fun `should serialize and deserialize Annotations round trip`() {
        val original = Annotations(
            audience = listOf(Role.User, Role.Assistant),
            priority = 0.42,
            lastModified = "2025-01-12T15:00:58Z",
        )

        verifySerializationRoundTrip(original, McpJson)
    }
}
