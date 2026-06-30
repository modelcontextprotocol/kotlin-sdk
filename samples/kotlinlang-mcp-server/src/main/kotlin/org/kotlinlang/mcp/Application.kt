package org.kotlinlang.mcp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import org.kotlinlang.mcp.config.toServerConfig

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = environment.config.toServerConfig()
    val kotlinlangServer = KotlinlangServer(config)

    monitor.subscribe(ApplicationStopped) {
        kotlinlangServer.close()
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    mcpStreamableHttp {
        kotlinlangServer.server
    }
}
