package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.atomic.AtomicInteger

class ServerListChangedNotificationTest {

    @ParameterizedTest
    @EnumSource(FeatureKind::class)
    fun `each registry change should notify only when listChanged is declared`(kind: FeatureKind): Unit = runBlocking {
        for (listChanged in listOf(true, false)) {
            withClue("listChanged=$listChanged") {
                val server = Server(Implementation("test server", "1.0"), ServerOptions(kind.capabilities(listChanged)))
                val client = Client(Implementation("test client", "1.0"))
                val notifications = AtomicInteger()
                client.setNotificationHandler<Notification>(kind.listChangedMethod) {
                    notifications.incrementAndGet()
                    CompletableDeferred(Unit)
                }
                connect(server, client)

                kind.add(server, "a")
                kind.addAll(server, listOf("b", "c"))
                shouldThrow<IllegalArgumentException> { kind.add(server, "a") }
                kind.remove(server, "a")
                kind.removeAll(server, listOf("b", "c", "absent"))
                kind.remove(server, "absent")
                server.close() // delivers all pending notifications before returning

                notifications.get() shouldBe if (listChanged) 6 else 0
            }
        }
    }
}
