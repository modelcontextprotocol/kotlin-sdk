package io.modelcontextprotocol.kotlin.sdk.conformance

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.conformance.auth.registerAuthScenarios
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

typealias ScenarioHandler = suspend (serverUrl: String) -> Unit

val scenarioHandlers = mutableMapOf<String, ScenarioHandler>()

// ============================================================================
// Main entry point
// ============================================================================

fun main(args: Array<String>) {
    val scenarioName = System.getenv("MCP_CONFORMANCE_SCENARIO")
    val serverUrl = args.lastOrNull()

    // Register all scenario handlers
    registerCoreScenarios()
    registerAuthScenarios()

    if (scenarioName == null || serverUrl == null) {
        logger.error { "Usage: MCP_CONFORMANCE_SCENARIO=<scenario> conformance-client <server-url>" }
        logger.error { "\nThe MCP_CONFORMANCE_SCENARIO env var is set automatically by the conformance runner." }
        logger.error { "\nAvailable scenarios:" }
        for (name in scenarioHandlers.keys.sorted()) {
            logger.error { "  - $name" }
        }
        exitProcess(1)
    }

    val handler = scenarioHandlers[scenarioName]
    if (handler == null) {
        logger.error { "Unknown scenario: $scenarioName" }
        logger.error { "\nAvailable scenarios:" }
        for (name in scenarioHandlers.keys.sorted()) {
            logger.error { "  - $name" }
        }
        exitProcess(1)
    }

    try {
        runBlocking {
            handler(serverUrl)
        }
        exitProcess(0)
    } catch (e: Exception) {
        logger.error(e) { "Error: ${e.message}" }
        exitProcess(1)
    }
}

// ============================================================================
// Basic scenarios (initialize, tools_call)
// ============================================================================

private suspend fun runBasicClient(serverUrl: String) {
    val httpClient = HttpClient(CIO) { install(SSE) }
    try {
        val transport = StreamableHttpClientTransport(httpClient, serverUrl)
        val client = Client(
            clientInfo = Implementation("test-client", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        client.connect(transport)
        client.listTools()
        client.close()
    } finally {
        httpClient.close()
    }
}

private suspend fun runToolsCallClient(serverUrl: String) {
    val httpClient = HttpClient(CIO) { install(SSE) }
    httpClient.use { httpClient ->
        val transport = StreamableHttpClientTransport(httpClient, serverUrl)
        val client = Client(
            clientInfo = Implementation("test-client", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        client.connect(transport)

        val tools = client.listTools()
        val addTool = tools.tools.find { it.name == "add_numbers" }
        if (addTool != null) {
            client.callTool(
                CallToolRequest(
                    CallToolRequestParams(
                        name = "add_numbers",
                        arguments = buildJsonObject {
                            put("a", 5)
                            put("b", 3)
                        },
                    ),
                ),
            )
        }

        client.close()
    }
}

// ============================================================================
// Elicitation defaults scenario
// ============================================================================

private suspend fun runElicitationDefaultsClient(serverUrl: String) {
    HttpClient(CIO) { install(SSE) }.use { httpClient ->
        val transport = StreamableHttpClientTransport(httpClient, serverUrl)
        val client = Client(
            clientInfo = Implementation("elicitation-defaults-test-client", "1.0.0"),
            options = ClientOptions(
                capabilities = ClientCapabilities(
                    elicitation = ClientCapabilities.elicitation,
                ),
            ),
        )

        // Register elicitation handler that returns empty content — SDK should fill in defaults
        client.setElicitationHandler { _ ->
            ElicitResult(
                action = ElicitResult.Action.Accept,
                content = JsonObject(emptyMap()),
            )
        }

        client.connect(transport)

        val tools = client.listTools()
        val testTool = tools.tools.find { it.name == "test_client_elicitation_defaults" }
            ?: error("Test tool not found: test_client_elicitation_defaults")

        client.callTool(
            CallToolRequest(CallToolRequestParams(name = testTool.name)),
        )

        client.close()
    }
}

// ============================================================================
// SSE retry scenario
// ============================================================================

private suspend fun runSSERetryClient(serverUrl: String) {
    HttpClient(CIO) { install(SSE) }.use { httpClient ->
        val transport = StreamableHttpClientTransport(httpClient, serverUrl)
        val client = Client(
            clientInfo = Implementation("sse-retry-test-client", "1.0.0"),
            options = ClientOptions(capabilities = ClientCapabilities()),
        )
        client.connect(transport)

        client.listTools()

        client.callTool(
            CallToolRequest(CallToolRequestParams(name = "test_reconnection")),
        )

        client.close()
    }
}

// ============================================================================
// Register core scenarios
// ============================================================================

private fun registerCoreScenarios() {
    scenarioHandlers["initialize"] = ::runBasicClient
    scenarioHandlers["tools_call"] = ::runToolsCallClient
    scenarioHandlers["elicitation-sep1034-client-defaults"] = ::runElicitationDefaultsClient
    scenarioHandlers["sse-retry"] = ::runSSERetryClient
}
