package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommonTypeTest {

    @Test
    fun `should have correct latest protocol version`() {
        assertNotEquals("", LATEST_PROTOCOL_VERSION)
        assertEquals("2025-06-18", LATEST_PROTOCOL_VERSION)
    }

    @Test
    fun `should have correct supported protocol versions`() {
        assertIs<List<String>>(SUPPORTED_PROTOCOL_VERSIONS)
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains(LATEST_PROTOCOL_VERSION))
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains("2025-03-26"))
        assertTrue(SUPPORTED_PROTOCOL_VERSIONS.contains("2024-11-05"))
        assertEquals(3, SUPPORTED_PROTOCOL_VERSIONS.size)
    }

    @Test
    fun `should serialize Icon with minimal fields`() {
        val icon = Icon(src = "https://example.com/icon.png")
        val json = McpJson.encodeToString(icon)

        json shouldEqualJson """
            {
              "src": "https://example.com/icon.png"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Icon with all fields`() {
        val icon = Icon(
            src = "https://example.com/icon.png",
            mimeType = "image/png",
            sizes = listOf("48x48", "96x96"),
            theme = Icon.Theme.Light,
        )
        val json = McpJson.encodeToString(icon)

        json shouldEqualJson """
            {
              "src": "https://example.com/icon.png",
              "mimeType": "image/png",
              "sizes": ["48x48", "96x96"],
              "theme": "light"
            }
        """.trimIndent()
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

        val icon = McpJson.decodeFromString<Icon>(json)

        assertEquals("https://example.com/icon.png", icon.src)
        assertEquals("image/png", icon.mimeType)
        assertEquals(listOf("48x48"), icon.sizes)
        assertEquals(Icon.Theme.Dark, icon.theme)
    }

    @Test
    fun `should deserialize Icon with minimal fields from JSON`() {
        val json = """{"src": "https://example.com/icon.png"}"""

        val icon = McpJson.decodeFromString<Icon>(json)

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
        val user = McpJson.decodeFromString<Role>("\"user\"")
        val assistant = McpJson.decodeFromString<Role>("\"assistant\"")

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

        val json = McpJson.encodeToString(annotations)

        json shouldEqualJson """
            {
              "audience": ["user", "assistant"],
              "priority": 0.8,
              "lastModified": "2025-01-12T15:00:58Z"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Annotations with minimal fields`() {
        val annotations = Annotations(priority = 0.5)

        val json = McpJson.encodeToString(annotations)

        json shouldEqualJson """
            {
              "priority": 0.5
            }
        """.trimIndent()
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

        val annotations = McpJson.decodeFromString<Annotations>(json)

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

        val json = McpJson.encodeToString(original)
        val decoded = McpJson.decodeFromString<Annotations>(json)

        assertEquals(original.audience, decoded.audience)
        assertEquals(original.priority, decoded.priority)
        assertEquals(original.lastModified, decoded.lastModified)
    }
}
