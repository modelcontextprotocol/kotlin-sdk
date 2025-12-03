package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.modelcontextprotocol.kotlin.sdk.client.AbstractClientTransportLifecycleTest
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.io.Buffer
import kotlin.test.Ignore
import kotlin.test.Test

class StdioClientTransportLifecycleTest : AbstractClientTransportLifecycleTest<StdioClientTransport>() {

    /**
     * Dummy method to make IDE treat this class as a test
     */
    @Test
    @Ignore
    fun dummyTest() = Unit

    override fun createTransport(): StdioClientTransport {
        val inputBuffer = Buffer()
        val outputBuffer = Buffer()
        return StdioClientTransport(
            input = inputBuffer,
            output = outputBuffer,
        )
    }
}
