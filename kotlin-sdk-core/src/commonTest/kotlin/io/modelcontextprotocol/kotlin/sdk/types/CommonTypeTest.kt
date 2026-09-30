package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.modelcontextprotocol.kotlin.test.utils.verifySerialization
import kotlin.test.Test

class CommonTypeTest {

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
    fun `should accept Annotations priority only between 0 and 1`() {
        shouldNotThrowAny {
            Annotations(priority = 0.0)
            Annotations(priority = 1.0)
        }
        listOf(-0.1, 1.1).forEach { priority ->
            withClue("priority=$priority") {
                shouldThrow<IllegalArgumentException> { Annotations(priority = priority) }
            }
        }
    }
}
