package client

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.Socket

class ClientIntegrationTest {

    fun createTransport(): StdioClientTransport {
        val socket = Socket("localhost", 3000)

        return StdioClientTransport(
            socket.inputStream.asSource().buffered(),
            socket.outputStream.asSink().buffered(),
        )
    }

    @Disabled("This test requires a running server")
    @Test
    fun testRequestTools() = runTest {
        val client = Client(
            Implementation("test", "1.0"),
        )

        val transport = createTransport()
        try {
            client.connect(transport)

            val response: ListToolsResult = client.listTools()
            println(response.tools)
        } finally {
            transport.close()
        }
    }
}
