package io.modelcontextprotocol.kotlin.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.types.DEFAULT_NEGOTIATED_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds

class StreamableHttpAndroidDeviceTest {
    @Test
    fun discoversAndCallsAToolOverHttps() = runBlocking {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()

        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.HTTP_1_1)
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.dispatcher = McpDispatcher()
            server.start(InetAddress.getByName("localhost"), 0)
            val url = "https://localhost:${server.port}/mcp"

            val httpClient = HttpClient(CIO) {
                install(SSE)
                engine {
                    https {
                        trustManager = clientTls.trustManager
                    }
                }
            }
            try {
                withTimeout(20.seconds) {
                    val client = Client(Implementation(name = "android-device-test", version = "1"))
                    try {
                        val transport = StreamableHttpClientTransport(httpClient, url) {
                            header("X-MCP-Android-Test", "1")
                        }
                        client.connect(transport)
                        assertEquals(listOf("echo"), client.listTools().tools.map { it.name })
                        val result = client.callTool("echo", emptyMap())
                        assertFalse(result.isError == true)
                        assertEquals(listOf(TextContent("android-ok")), result.content)
                    } finally {
                        client.close()
                    }
                }
            } finally {
                httpClient.close()
            }
        }
    }

    private class McpDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path != "/mcp") return MockResponse().setResponseCode(404)
            if (request.method != "POST") return MockResponse().setResponseCode(405)
            val message = McpJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            val method = message.getValue("method").jsonPrimitive.content
            val protocolVersion = request.getHeader("MCP-Protocol-Version")

            if (request.getHeader("X-MCP-Android-Test") != "1" ||
                (method != "initialize" && protocolVersion != DEFAULT_NEGOTIATED_PROTOCOL_VERSION)
            ) {
                return MockResponse().setResponseCode(400)
            }

            val result = when (method) {
                "initialize" -> """
                    {
                        "protocolVersion":"$DEFAULT_NEGOTIATED_PROTOCOL_VERSION",
                        "capabilities":{"tools":{}},
                        "serverInfo":{"name":"android-device-fixture","version":"1"}
                    }
                """.trimIndent()

                "notifications/initialized" -> return MockResponse().setResponseCode(202)

                "tools/list" -> """{"tools":[{"name":"echo","inputSchema":{"type":"object"}}]}"""

                "tools/call" -> {
                    val tool = message["params"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    if (tool != "echo") return MockResponse().setResponseCode(400)
                    """{"content":[{"type":"text","text":"android-ok"}]}"""
                }

                else -> return MockResponse().setResponseCode(400)
            }
            val response = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", message.getValue("id"))
                put("result", McpJson.parseToJsonElement(result))
            }.toString()
            return if (method == "tools/call") {
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("event: message\ndata: $response\n\n")
            } else {
                MockResponse().setHeader("Content-Type", "application/json").setBody(response)
            }
        }
    }
}
