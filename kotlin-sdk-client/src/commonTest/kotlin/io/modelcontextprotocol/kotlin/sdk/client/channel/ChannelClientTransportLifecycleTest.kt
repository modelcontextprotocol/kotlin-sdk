package io.modelcontextprotocol.kotlin.sdk.client.channel

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.AbstractClientTransportLifecycleTest
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import kotlin.test.Ignore
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ChannelClientTransportLifecycleTest : AbstractClientTransportLifecycleTest<ChannelTransport>() {

    /**
     * Dummy method to make IDE treat this class as a test
     */
    @Test
    @Ignore
    fun dummyTest() = Unit

    override fun createTransport(): ChannelTransport = ChannelTransport()
}
