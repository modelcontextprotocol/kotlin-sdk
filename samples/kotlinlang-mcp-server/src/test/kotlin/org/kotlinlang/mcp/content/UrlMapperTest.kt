package org.kotlinlang.mcp.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UrlMapperTest {

    @Test
    fun `top-level path maps to _llms URL`() {
        assertEquals(
            "https://kotlinlang.org/docs/_llms/coroutines-overview.txt",
            mapPathToUrl("coroutines-overview"),
        )
    }

    @Test
    fun `nested path maps to _llms URL`() {
        assertEquals(
            "https://kotlinlang.org/docs/multiplatform/_llms/compose.txt",
            mapPathToUrl("multiplatform/compose"),
        )
    }

    @Test
    fun `deep nested path maps to _llms URL`() {
        assertEquals(
            "https://kotlinlang.org/docs/a/b/_llms/c.txt",
            mapPathToUrl("a/b/c"),
        )
    }

    @Test
    fun `trims leading and trailing slashes`() {
        assertEquals(
            "https://kotlinlang.org/docs/_llms/coroutines-overview.txt",
            mapPathToUrl("/coroutines-overview/"),
        )
    }

    @Test
    fun `strips html extension`() {
        assertEquals(
            "https://kotlinlang.org/docs/_llms/coroutines-overview.txt",
            mapPathToUrl("coroutines-overview.html"),
        )
    }

    @Test
    fun `normalizes slashes and html extension together`() {
        assertEquals(
            "https://kotlinlang.org/docs/multiplatform/_llms/compose.txt",
            mapPathToUrl("/multiplatform/compose.html"),
        )
    }

    @Test
    fun `empty string throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            mapPathToUrl("")
        }
    }

    @Test
    fun `only slashes throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            mapPathToUrl("///")
        }
    }

    @Test
    fun `blank path throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            mapPathToUrl("   ")
        }
    }

    @Test
    fun `path traversal throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            mapPathToUrl("../../secret")
        }
    }

    @Test
    fun `path traversal in the middle throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            mapPathToUrl("multiplatform/../secret")
        }
    }
}
