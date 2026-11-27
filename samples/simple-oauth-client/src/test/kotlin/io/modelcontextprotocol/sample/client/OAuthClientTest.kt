package io.modelcontextprotocol.sample.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the sample against an in-process MCP server protected by a minimal OAuth authorization server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuthClientTest {

    private val authServer = FakeAuthorizationServer()
    private val server = embeddedServer(io.ktor.server.cio.CIO, host = "127.0.0.1", port = 0) {
        authServer.install(this)
    }
    private lateinit var baseUrl: String
    private val serverUrl get() = "$baseUrl/mcp"

    // Plays the user's browser: follows the authorization redirect to the loopback callback.
    private val browser = HttpClient(CIO) { followRedirects = true }
    private val oauthHttp = HttpClient(CIO)

    @BeforeAll
    fun startServer() {
        server.start(wait = false)
        baseUrl = runBlocking { "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}" }
        authServer.baseUrl = baseUrl
    }

    @AfterAll
    fun stopServer() {
        browser.close()
        oauthHttp.close()
        server.stop(100, 1_000)
    }

    private fun oauthFlow() = OAuthFlow(
        httpClient = oauthHttp,
        openBrowser = { url -> runBlocking { browser.get(url) } },
        timeout = 10.seconds,
    )

    @Test
    fun `should sign in and call a protected tool`(): Unit = runBlocking {
        authorizedHttpClient(serverUrl, oauthFlow()).use { http ->
            val client = http.mcpStreamableHttp(serverUrl)
            try {
                assertEquals(listOf("whoami"), client.listTools().tools.map { it.name })

                val result = client.callTool(name = "whoami", arguments = emptyMap())
                val text = assertIs<TextContent>(result.content.single()).text
                assertTrue(text.startsWith("client-"), "unexpected tool output: $text")
            } finally {
                client.close()
            }
        }

        val authorize = authServer.authorizeRequests.last()
        assertEquals("S256", authorize["code_challenge_method"])
        assertEquals(serverUrl, authorize["resource"])
        assertEquals("mcp:tools", authorize["scope"])
        assertTrue(authorize["redirect_uri"]!!.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun `should reject a callback with the wrong state`(): Unit = runBlocking {
        val flow = OAuthFlow(
            httpClient = oauthHttp,
            openBrowser = { url ->
                val tampered = URLBuilder(url).apply { parameters["state"] = "forged" }.buildString()
                runBlocking { browser.get(tampered) }
            },
            timeout = 10.seconds,
        )
        val error = assertFailsWith<IllegalStateException> { flow.authorize(serverUrl, null) }
        assertEquals("Authorization response has an unexpected state", error.message)
    }

    @Test
    fun `should compute the RFC 7636 S256 code challenge`() {
        // Test vector from RFC 7636, appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `should read parameters from a WWW-Authenticate header`() {
        val header = "Bearer error=\"invalid_token\", " +
            "resource_metadata=\"https://example.com/.well-known/x\", scope=\"a b\""
        assertEquals("https://example.com/.well-known/x", header.authParam("resource_metadata"))
        assertEquals("a b", header.authParam("scope"))
        assertEquals("invalid_token", header.authParam("error"))
        assertEquals(null, header.authParam("realm"))
    }
}

/**
 * Just enough of an OAuth 2.1 authorization server, plus a protected MCP endpoint, to exercise the client.
 * It approves every authorization request without showing a login page.
 */
private class FakeAuthorizationServer {
    lateinit var baseUrl: String
    val authorizeRequests = CopyOnWriteArrayList<Map<String, String>>()
    private val clients = ConcurrentHashMap<String, String>() // client_id -> redirect_uri
    private val codes = ConcurrentHashMap<String, Map<String, String>>() // code -> authorize params
    private val tokens = ConcurrentHashMap<String, String>() // access token -> client_id
    private val json = Json

    fun install(app: Application) = with(app) {
        // Guards the MCP endpoint like a resource server would.
        intercept(ApplicationCallPipeline.Plugins) {
            if (!call.request.path().startsWith("/mcp")) return@intercept
            val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
            if (token == null || !tokens.containsKey(token)) {
                val metadataUrl = "$baseUrl/.well-known/oauth-protected-resource/mcp"
                call.response.header(
                    HttpHeaders.WWWAuthenticate,
                    """Bearer resource_metadata="$metadataUrl", scope="mcp:tools"""",
                )
                call.respond(HttpStatusCode.Unauthorized)
                finish()
            }
        }

        routing {
            get("/.well-known/oauth-protected-resource/mcp") {
                call.respondJson(
                    """
                    {"resource":"$baseUrl/mcp","authorization_servers":["$baseUrl"],
                     "scopes_supported":["mcp:tools"]}
                    """.trimIndent(),
                )
            }
            get("/.well-known/oauth-authorization-server") {
                call.respondJson(
                    """
                    {"issuer":"$baseUrl","authorization_endpoint":"$baseUrl/authorize",
                     "token_endpoint":"$baseUrl/token","registration_endpoint":"$baseUrl/register",
                     "code_challenge_methods_supported":["S256"],
                     "response_types_supported":["code"],"grant_types_supported":["authorization_code"]}
                    """.trimIndent(),
                )
            }
            post("/register") {
                val body = json.parseToJsonElement(call.receiveText()).jsonObject
                val redirectUri = body["redirect_uris"]!!.jsonArray.single().jsonPrimitive.content
                val clientId = "client-${clients.size + 1}"
                clients[clientId] = redirectUri
                call.respondJson(
                    """{"client_id":"$clientId","redirect_uris":["$redirectUri"]}""",
                    HttpStatusCode.Created,
                )
            }
            get("/authorize") {
                val params = call.request.queryParameters.entries().associate { it.key to it.value.single() }
                authorizeRequests += params
                val redirectUri = params.getValue("redirect_uri")
                require(clients[params["client_id"]] == redirectUri) { "unknown client or redirect URI" }
                val code = "code-${codes.size + 1}"
                codes[code] = params
                val target = URLBuilder(redirectUri).apply {
                    parameters.append("code", code)
                    parameters.append("state", params.getValue("state"))
                }.buildString()
                call.respondRedirect(target)
            }
            post("/token") {
                val form = call.receiveParameters()
                val issued = codes.remove(form["code"])
                val valid = issued != null &&
                    form["grant_type"] == "authorization_code" &&
                    form["client_id"] == issued["client_id"] &&
                    form["redirect_uri"] == issued["redirect_uri"] &&
                    form["resource"] == issued["resource"] &&
                    codeChallenge(form["code_verifier"].orEmpty()) == issued["code_challenge"]
                if (!valid) {
                    call.respondJson("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
                    return@post
                }
                val token = "token-${tokens.size + 1}"
                tokens[token] = issued.getValue("client_id")
                call.respondJson("""{"access_token":"$token","token_type":"Bearer","expires_in":3600}""")
            }
        }

        mcpStreamableHttp {
            val clientId = call.request.headers[HttpHeaders.Authorization]
                ?.removePrefix("Bearer ")?.let { tokens[it] }.orEmpty()
            Server(
                Implementation(name = "protected-server", version = "1.0.0"),
                ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools())),
            ).apply {
                addTool(name = "whoami", description = "Returns the OAuth client that called the tool") {
                    CallToolResult(content = listOf(TextContent(clientId)))
                }
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respondText(body, io.ktor.http.ContentType.Application.Json, status)
}
