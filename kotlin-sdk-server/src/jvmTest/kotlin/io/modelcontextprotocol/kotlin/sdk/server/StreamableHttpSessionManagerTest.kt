package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class StreamableHttpSessionManagerTest {

    private val timeSource = TestTimeSource()
    private val idleTimeout = 10.minutes

    private fun sessionManager(maxSessions: Int = 10) = StreamableHttpSessionManager<Transport>(
        idleTimeout = idleTimeout,
        maxSessions = maxSessions,
        timeSource = timeSource,
    )

    private fun newTransport() = StreamableHttpServerTransport(StreamableHttpServerTransport.Configuration())

    /** Opens a session the way an initialize request does, then completes that request. */
    private fun StreamableHttpSessionManager<Transport>.openIdleSession(
        sessionId: String,
        transport: Transport = newTransport(),
    ): Transport {
        tryReserve() shouldBe true
        register(sessionId, transport)
        release(sessionId, transport)
        return transport
    }

    @Test
    fun `idle session is closed once the idle timeout elapses`() = runTest {
        val sessions = sessionManager()
        val transport = sessions.openIdleSession("session")
        var transportClosed = false
        transport.onClose { transportClosed = true }

        timeSource += idleTimeout - 1.milliseconds
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe true
        transportClosed shouldBe false

        timeSource += 1.milliseconds
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe false
        transportClosed shouldBe true
    }

    @Test
    fun `session does not expire while its initializing request is in flight`() = runTest {
        val sessions = sessionManager()
        sessions.tryReserve() shouldBe true
        val transport = newTransport()
        sessions.register("session", transport)

        timeSource += idleTimeout * 2
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe true

        sessions.release("session", transport)
        timeSource += idleTimeout
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe false
    }

    @Test
    fun `session does not expire while a request is in flight`() = runTest {
        val sessions = sessionManager()
        sessions.openIdleSession("session")

        sessions.withSession("session") {
            // A long-running request.
            timeSource += idleTimeout * 2
            sessions.closeExpiredSessions()
            ("session" in sessions) shouldBe true
        } shouldBe true

        timeSource += idleTimeout
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe false
    }

    @Test
    fun `a request restarts the idle timer`() = runTest {
        val sessions = sessionManager()
        sessions.openIdleSession("session")

        timeSource += idleTimeout - 1.milliseconds
        sessions.withSession("session") {} shouldBe true

        timeSource += idleTimeout - 1.milliseconds
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe true

        timeSource += 1.milliseconds
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe false
    }

    @Test
    fun `touch restarts the idle timer without keeping the session open`() = runTest {
        val sessions = sessionManager()
        val transport = sessions.openIdleSession("session")

        timeSource += idleTimeout - 1.milliseconds
        // A GET stream opens and stays open.
        sessions.touch("session") shouldBe transport

        timeSource += idleTimeout - 1.milliseconds
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe true

        timeSource += 1.milliseconds
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe false
    }

    @Test
    fun `an unknown or removed session cannot be reached`() = runTest {
        val sessions = sessionManager()
        var ran = false
        sessions.touch("unknown") shouldBe null
        sessions.withSession("unknown") { ran = true } shouldBe false

        val transport = sessions.openIdleSession("session")
        sessions.remove("session", transport)
        sessions.touch("session") shouldBe null
        sessions.withSession("session") { ran = true } shouldBe false
        ran shouldBe false
    }

    @Test
    fun `new sessions are refused while maxSessions are open`() {
        val sessions = sessionManager(maxSessions = 2)
        sessions.tryReserve() shouldBe true
        sessions.tryReserve() shouldBe true
        sessions.tryReserve() shouldBe false

        sessions.cancelReservation()
        sessions.tryReserve() shouldBe true
    }

    @Test
    fun `removing a session frees its slot once`() {
        val sessions = sessionManager(maxSessions = 2)
        val transport = sessions.openIdleSession("first")
        sessions.openIdleSession("second")
        sessions.tryReserve() shouldBe false

        sessions.remove("first", transport)
        sessions.remove("first", transport)
        ("first" in sessions) shouldBe false
        sessions.tryReserve() shouldBe true
        sessions.tryReserve() shouldBe false
    }

    @Test
    fun `expired session frees its slot`() = runTest {
        val sessions = sessionManager(maxSessions = 1)
        sessions.openIdleSession("session")
        sessions.tryReserve() shouldBe false

        timeSource += idleTimeout
        sessions.closeExpiredSessions()
        sessions.tryReserve() shouldBe true
    }

    @Test
    fun `remove ignores a transport that does not serve the session`() {
        val sessions = sessionManager()
        sessions.openIdleSession("session")

        sessions.remove("session", newTransport())
        ("session" in sessions) shouldBe true
    }

    @Test
    fun `a close that hangs or fails does not hold up expiry`() = runTest {
        val sessions = sessionManager()
        val hanging = HangingTransport()
        sessions.openIdleSession("hanging", hanging)
        sessions.openIdleSession("failing", FailingTransport())
        var closed = false
        sessions.openIdleSession("session").onClose { closed = true }
        timeSource += idleTimeout

        val expiry = launch { sessions.closeExpiredSessionsPeriodically() }
        try {
            advanceTimeBy(sessions.expiryCheckInterval + 1.milliseconds)
            hanging.closing.isCompleted shouldBe true
            closed shouldBe true

            // Expiry carries on while that close still hangs.
            var laterClosed = false
            sessions.openIdleSession("later").onClose { laterClosed = true }
            timeSource += idleTimeout
            advanceTimeBy(sessions.expiryCheckInterval)
            laterClosed shouldBe true
        } finally {
            expiry.cancel()
        }
    }

    @Test
    fun `infinite idle timeout never expires sessions`() = runTest {
        val sessions = StreamableHttpSessionManager<Transport>(idleTimeout = Duration.INFINITE, timeSource = timeSource)
        sessions.expires shouldBe false
        sessions.openIdleSession("session")

        timeSource += 365.days
        sessions.closeExpiredSessions()
        ("session" in sessions) shouldBe true
    }

    @Test
    fun `expiry is checked at least every five seconds`() {
        StreamableHttpSessionManager<Transport>(idleTimeout = 30.minutes).expiryCheckInterval shouldBe 5.seconds
        StreamableHttpSessionManager<Transport>(idleTimeout = 1.seconds).expiryCheckInterval shouldBe 1.seconds
        StreamableHttpSessionManager<Transport>(idleTimeout = 1.nanoseconds).expiryCheckInterval shouldBe
            10.milliseconds
    }

    @Test
    fun `expiry never closes a session while a request runs on it`(): Unit = runBlocking(Dispatchers.Default) {
        // Every idle moment is past the timeout, so expiry races the start of every request.
        val sessions = StreamableHttpSessionManager<Transport>(idleTimeout = 1.nanoseconds, maxSessions = 1)
        repeat(200) { round ->
            val sessionId = "session-$round"
            val transport = newTransport()
            val transportClosed = AtomicBoolean(false)
            transport.onClose { transportClosed.set(true) }
            sessions.openIdleSession(sessionId, transport)

            val requestRunning = CompletableDeferred<Unit>()
            val requests = List(4) {
                launch {
                    repeat(50) {
                        sessions.withSession(sessionId) {
                            requestRunning.complete(Unit)
                            transportClosed.get() shouldBe false
                            yield()
                            transportClosed.get() shouldBe false
                        }
                    }
                }
            }
            // Start expiring only once a request runs, so that expiry races requests instead of beating them all.
            requestRunning.await()
            while (sessionId in sessions) {
                sessions.closeExpiredSessions()
                yield()
            }
            requests.joinAll()
            transportClosed.get() shouldBe true
        }
    }

    /** A transport whose close never finishes, like one stuck on a client that stopped reading. */
    private class HangingTransport : AbstractTransport() {
        val closing = CompletableDeferred<Unit>()

        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) = Unit

        override suspend fun close() {
            closing.complete(Unit)
            awaitCancellation()
        }
    }

    private class FailingTransport : AbstractTransport() {
        override suspend fun start() = Unit

        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) = Unit

        override suspend fun close(): Unit = error("close failed")
    }
}
