package io.modelcontextprotocol.kotlin.sdk.client.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class McpOAuthTest {

    @Test
    fun `should build protected resource metadata URLs in priority order`() {
        val urls = mcpProtectedResourceMetadataUrls("https://mcp.example.com/public/mcp")

        assertEquals(
            listOf(
                "https://mcp.example.com/.well-known/oauth-protected-resource/public/mcp",
                "https://mcp.example.com/.well-known/oauth-protected-resource",
            ),
            urls,
        )
    }

    @Test
    fun `should build authorization server metadata URLs in priority order`() {
        val urls = mcpAuthorizationServerMetadataUrls("https://auth.example.com/tenant1")

        assertEquals(
            listOf(
                "https://auth.example.com/.well-known/oauth-authorization-server/tenant1",
                "https://auth.example.com/.well-known/openid-configuration/tenant1",
                "https://auth.example.com/tenant1/.well-known/openid-configuration",
            ),
            urls,
        )
    }

    @Test
    fun `should discover resource metadata using root fallback`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedUrls += request.url.toString()
            when (request.url.toString()) {
                "https://mcp.example.com/.well-known/oauth-protected-resource/public/mcp" ->
                    respond("", status = HttpStatusCode.NotFound)

                "https://mcp.example.com/.well-known/oauth-protected-resource" -> respondJson(
                    """
                    {
                      "resource": "https://mcp.example.com",
                      "authorization_servers": ["https://auth.example.com"],
                      "scopes_supported": ["files:read", "files:write"]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        })

        val metadata = discoverMcpProtectedResourceMetadata(client, "https://mcp.example.com/public/mcp")

        assertEquals(
            listOf(
                "https://mcp.example.com/.well-known/oauth-protected-resource/public/mcp",
                "https://mcp.example.com/.well-known/oauth-protected-resource",
            ),
            requestedUrls,
        )
        assertEquals("https://mcp.example.com", metadata.resource)
        assertEquals(listOf("https://auth.example.com"), metadata.authorizationServers)
        assertEquals(listOf("files:read", "files:write"), metadata.scopesSupported)
    }

    @Test
    fun `should discover oauth metadata with protected resource metadata override`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedUrls += request.url.toString()
            when (request.url.toString()) {
                "https://mcp.example.com/custom-resource-metadata" -> respondJson(
                    """
                    {
                      "resource": "https://mcp.example.com/mcp",
                      "authorization_servers": ["https://auth.example.com/tenant"],
                      "scopes_supported": ["mcp:read"]
                    }
                    """.trimIndent(),
                )

                "https://auth.example.com/.well-known/oauth-authorization-server/tenant" ->
                    respond("", status = HttpStatusCode.NotFound)

                "https://auth.example.com/.well-known/openid-configuration/tenant" -> respondJson(
                    """
                    {
                      "issuer": "https://auth.example.com/tenant",
                      "authorization_endpoint": "https://auth.example.com/authorize",
                      "token_endpoint": "https://auth.example.com/token",
                      "token_endpoint_auth_methods_supported": ["client_secret_post"],
                      "code_challenge_methods_supported": ["S256"],
                      "client_id_metadata_document_supported": true
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        })

        val result = discoverMcpOAuthMetadata(
            httpClient = client,
            serverUrl = "https://mcp.example.com/mcp",
            resourceMetadataUrl = "https://mcp.example.com/custom-resource-metadata",
        )

        assertEquals(
            listOf(
                "https://mcp.example.com/custom-resource-metadata",
                "https://auth.example.com/.well-known/oauth-authorization-server/tenant",
                "https://auth.example.com/.well-known/openid-configuration/tenant",
            ),
            requestedUrls,
        )
        assertEquals("https://mcp.example.com/mcp", result.resourceMetadata.resource)
        assertEquals("https://auth.example.com/token", result.authorizationServerMetadata.tokenEndpoint)
        assertEquals(listOf("S256"), result.authorizationServerMetadata.codeChallengeMethodsSupported)
        assertEquals(true, result.authorizationServerMetadata.clientIdMetadataDocumentSupported)
    }

    @Test
    fun `should parse bearer challenge parameters`() {
        val header = """Bearer resource_metadata="https://mcp.example.com/.well-known/oauth-protected-resource", scope="files:read files:write""""

        assertEquals(
            "https://mcp.example.com/.well-known/oauth-protected-resource",
            wwwAuthenticateParameter(header, "resource_metadata"),
        )
        assertEquals("files:read files:write", wwwAuthenticateParameter(header, "scope"))
    }

    @Test
    fun `should parse step up scope only for insufficient scope`() {
        val header = """Bearer error="insufficient_scope", scope="files:write", resource_metadata="https://mcp.example.com/.well-known/oauth-protected-resource""""

        assertEquals("files:write", mcpOAuthStepUpScope(header))
        assertNull(mcpOAuthStepUpScope("""Bearer error="invalid_token", scope="files:write""""))
    }

    @Test
    fun `should select challenge scope before metadata scopes`() {
        assertEquals(
            "files:read",
            selectMcpOAuthScope("files:read", listOf("files:read", "files:write")),
        )
        assertEquals(
            "files:read files:write",
            selectMcpOAuthScope(null, listOf("files:read", "files:write")),
        )
        assertNull(selectMcpOAuthScope(null, null))
    }

    @Test
    fun `should apply bearer authorization header`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
            respond("ok")
        })

        client.get("https://mcp.example.com/mcp") {
            mcpBearerAuth("token-123")(this)
        }
    }

    @Test
    fun `should fail when metadata cannot be discovered`() = runTest {
        val client = HttpClient(MockEngine {
            respond("", status = HttpStatusCode.NotFound)
        })

        assertFailsWith<McpOAuthException> {
            discoverMcpProtectedResourceMetadata(client, "https://mcp.example.com/mcp")
        }
    }

    @Test
    fun `should fail oauth discovery when protected resource metadata omits authorization servers`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedUrls += request.url.toString()
            when (request.url.toString()) {
                "https://mcp.example.com/.well-known/oauth-protected-resource/mcp" -> respondJson(
                    """
                    {
                      "resource": "https://mcp.example.com/mcp",
                      "scopes_supported": ["mcp:read"]
                    }
                    """.trimIndent(),
                )

                else -> error("Unexpected URL: ${request.url}")
            }
        })

        assertFailsWith<McpOAuthException> {
            discoverMcpOAuthMetadata(client, "https://mcp.example.com/mcp")
        }
        assertEquals(
            listOf("https://mcp.example.com/.well-known/oauth-protected-resource/mcp"),
            requestedUrls,
        )
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
