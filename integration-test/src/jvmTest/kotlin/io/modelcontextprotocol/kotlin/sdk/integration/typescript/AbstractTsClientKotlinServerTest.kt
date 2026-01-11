package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.kotest.assertions.nondeterministic.eventually
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

abstract class AbstractTsClientKotlinServerTest : TsTestBase() {

    protected open fun beforeServer() {}
    protected open fun afterServer() {}

    /**
     * Run the TypeScript client against the prepared server and return its console output.
     */
    protected abstract fun runClient(vararg args: String): String

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun toolCall() = runTest {
        beforeServer()
        try {
            val testName = "TestUser"
            val out = runClient("greet", testName)
            eventually(5.seconds) {
                assertTrue(out.contains("Text content:"), "Output should contain the text content section.\n$out")
                assertTrue(out.contains("Hello, $testName!"), "Tool response should contain the greeting.\n$out")
            }
            assertTrue(
                out.contains("Structured content:"),
                "Output should contain the structured content section.\n$out",
            )
            assertTrue(
                out.contains(
                    "\"greeting\": \"Hello, $testName!\"",
                ) ||
                    out.contains("greeting") ||
                    out.contains("greet"),
                "Structured content should contain the greeting.\n$out",
            )
        } finally {
            afterServer()
        }
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun notifications() = runBlocking {
        beforeServer()
        try {
            val name = "NotifUser"
            val out = runClient("multi-greet", name)
            assertTrue(
                out.contains("Multiple greetings") || out.contains("greeting"),
                "Tool response should contain greeting message.\n$out",
            )
            assertTrue(
                out.contains("\"notificationCount\": 3") || out.contains("notificationCount: 3"),
                "Structured content should indicate that 3 notifications were emitted by the server.\nOutput:\n$out",
            )
        } finally {
            afterServer()
        }
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun multipleClientSequence() = runBlocking {
        beforeServer()
        try {
            val out1 = runClient("greet", "FirstClient")
            assertTrue(out1.contains("Hello, FirstClient!"), "Should greet first client.\n$out1")

            val out2 = runClient("multi-greet", "SecondClient")
            assertTrue(
                out2.contains("Multiple greetings") || out2.contains("greeting"),
                "Should respond for second client.\n$out2",
            )

            val out3 = runClient()
            assertTrue(out3.contains("Available utils:"), "Should list available utils.\n$out3")
            assertTrue(out3.contains("greet"), "Greet tool should be available.\n$out3")
            assertTrue(out3.contains("multi-greet"), "Multi-greet tool should be available.\n$out3")
        } finally {
            afterServer()
        }
    }
}
