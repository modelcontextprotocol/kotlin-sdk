package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

abstract class AbstractClientTransportLifecycleTest<T : AbstractTransport> {

    protected lateinit var transport: T

    @BeforeTest
    fun beforeEach() {
        transport = createTransport()
    }

    @Test
    fun `should throw when started twice`() = runTest {
        transport.start()

        val exception = shouldThrow<IllegalStateException> {
            transport.start()
        }
        exception.message shouldContain "already started"
    }

    @Test
    fun `should be idempotent when closed twice`() = runTest {
        val transport = createTransport()

        transport.start()
        transport.close()

        // Second close should not throw
        transport.close()
    }

    @Test
    fun `should throw when sending before start`() = runTest {
        val transport = createTransport()

        val exception = shouldThrow<McpException> {
            transport.send(PingRequest().toJSON())
        }
        exception.message shouldContain "not started"
        exception.code shouldBe CONNECTION_CLOSED
    }

    @Test
    fun `should throw when sending after close`() = runTest {
        val transport = createTransport()

        transport.start()
        delay(50.milliseconds)
        transport.close()

        shouldThrow<Exception> {
            transport.send(PingRequest().toJSON())
        }
    }

    @Test
    fun `should call onClose exactly once`() = runTest {
        val transport = createTransport()

        var closeCallCount = 0
        transport.onClose { closeCallCount++ }

        transport.start()
        delay(50.milliseconds)

        // Multiple close attempts
        transport.close()
        transport.close()

        closeCallCount shouldBe 1
    }

    protected abstract fun createTransport(): T
}
