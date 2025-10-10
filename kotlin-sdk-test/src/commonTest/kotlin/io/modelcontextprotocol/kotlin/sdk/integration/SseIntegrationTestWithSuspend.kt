package io.modelcontextprotocol.kotlin.sdk.integration

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlin.collections.emptyList
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

class SseIntegrationTestWithSuspend : AbstractSseIntegrationTest() {

    private suspend fun suspendNewMcpServer(): Server {
        return newMcpServer()
    }

    protected override suspend fun initServer(): Pair<CIOEmbeddedServer, List<String>> {
        val ktorServer = embeddedServer(ServerCIO, host = URL, port = PORT) {
            install(ServerSSE)
            routing {
                mcp { 
                    suspendNewMcpServer()
                }
            }
        }

        return ktorServer.startSuspend(wait = false) to emptyList()
    }
}

