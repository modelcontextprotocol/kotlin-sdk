package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class AbstractStreamableHttpClientTest {

    // start mokksy on random port
    protected val mockMcp: OldSchemaMockMcp = OldSchemaMockMcp(verbose = true)

    @AfterEach
    fun afterEach() {
        mockMcp.checkForUnmatchedRequests()
    }

    protected suspend fun connect(client: Client) {
        client.connect(
            StreamableHttpClientTransport(
                url = mockMcp.url,
                client = HttpClient(Apache5) {
                    install(SSE)
                    install(Logging) {
                        level = LogLevel.ALL
                    }
                },
            ),
        )
    }
}
