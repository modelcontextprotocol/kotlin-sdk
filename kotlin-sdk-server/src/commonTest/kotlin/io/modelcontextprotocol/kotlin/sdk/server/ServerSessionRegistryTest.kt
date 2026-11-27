package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ServerSessionRegistryTest {

    private fun newSession(): ServerSession = ServerSession(
        serverInfo = Implementation("test-server", "1.0.0"),
        options = ServerOptions(capabilities = ServerCapabilities()),
        instructions = null,
    )

    @Test
    fun `new registry has no sessions`() {
        val registry = ServerSessionRegistry()

        registry.sessions.size shouldBe 0
    }

    @Test
    fun `added session can be retrieved by id`() {
        val registry = ServerSessionRegistry()
        val session = newSession()

        registry.addSession(session)

        assertSame(session, registry.getSession(session.sessionId))
        assertSame(session, registry.getSessionOrNull(session.sessionId))
        registry.sessions.size shouldBe 1
    }

    @Test
    fun `getSession throws for unknown id and mentions it in the message`() {
        val registry = ServerSessionRegistry()

        val ex = assertFailsWith<IllegalArgumentException> { registry.getSession("missing-id") }

        ex.message.orEmpty() shouldContain "missing-id"
    }

    @Test
    fun `getSessionOrNull returns null for unknown id`() {
        val registry = ServerSessionRegistry()

        assertNull(registry.getSessionOrNull("missing-id"))
    }

    @Test
    fun `removeSession removes only the requested session`() {
        val registry = ServerSessionRegistry()
        val first = newSession()
        val second = newSession()
        registry.addSession(first)
        registry.addSession(second)

        registry.removeSession(first.sessionId)

        assertNull(registry.getSessionOrNull(first.sessionId))
        assertSame(second, registry.getSessionOrNull(second.sessionId))
        registry.sessions.size shouldBe 1
    }

    @Test
    fun `removeSession with unknown id leaves the registry unchanged`() {
        val registry = ServerSessionRegistry()
        val session = newSession()
        registry.addSession(session)

        registry.removeSession("missing-id")

        assertSame(session, registry.getSessionOrNull(session.sessionId))
        registry.sessions.size shouldBe 1
    }

    @Test
    fun `sessions snapshot is not affected by later changes`() {
        val registry = ServerSessionRegistry()
        val snapshotBefore = registry.sessions
        val session = newSession()

        registry.addSession(session)

        snapshotBefore.size shouldBe 0
        registry.sessions.size shouldBe 1
    }
}
