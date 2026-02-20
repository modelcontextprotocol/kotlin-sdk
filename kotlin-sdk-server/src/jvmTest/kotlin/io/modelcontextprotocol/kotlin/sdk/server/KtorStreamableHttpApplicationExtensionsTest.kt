package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class KtorStreamableHttpApplicationExtensionsTest {

    @Test
    fun `Application mcpStreamableHttp should install SSE and register endpoints at default path`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
        }

        client.assertStreamableHttpEndpointsAt("/mcp")
    }

    @Test
    fun `Application mcpStreamableHttp should register endpoints at a custom path`() = testApplication {
        application {
            mcpStreamableHttp(path = "/api/v1/mcp") { testServer() }
        }

        client.assertStreamableHttpEndpointsAt("/api/v1/mcp")

        // Default path is not registered
        client.get("/mcp").shouldHaveStatus(HttpStatusCode.NotFound)
    }

    @Test
    fun `Application mcpStreamableHttp should coexist with other routes`() = testApplication {
        application {
            mcpStreamableHttp { testServer() }
            routing {
                get("/health") { call.respondText("healthy") }
            }
        }

        val healthResponse = client.get("/health")
        healthResponse.shouldHaveStatus(HttpStatusCode.OK)
        healthResponse.bodyAsText() shouldBe "healthy"

        client.assertStreamableHttpEndpointsAt("/mcp")
    }

    @Test
    fun `Application mcpStatelessStreamableHttp should install SSE and register endpoints at default path`() =
        testApplication {
            application {
                mcpStatelessStreamableHttp { testServer() }
            }

            client.assertStatelessStreamableHttpEndpointsAt("/mcp")
        }

    @Test
    fun `Application mcpStatelessStreamableHttp should register endpoints at a custom path`() = testApplication {
        application {
            mcpStatelessStreamableHttp(path = "/api/v1/mcp") { testServer() }
        }

        client.assertStatelessStreamableHttpEndpointsAt("/api/v1/mcp")

        // Default path is not registered
        client.get("/mcp").shouldHaveStatus(HttpStatusCode.NotFound)
    }
}
