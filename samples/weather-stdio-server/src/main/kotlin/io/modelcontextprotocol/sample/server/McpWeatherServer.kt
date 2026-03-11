package io.modelcontextprotocol.sample.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Starts an MCP server that provides weather-related tools for fetching active
 * weather alerts by state and weather forecasts by latitude/longitude.
 */
fun runMcpServer() {
    createHttpClient().use { httpClient ->
        val server = Server(
            Implementation(
                name = "weather",
                version = "1.0.0",
            ),
            ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
            ),
        )

        server.registerTools(httpClient)

        val transport = StdioServerTransport(
            System.`in`.asInput(),
            System.out.asSink().buffered(),
        )

        runBlocking {
            val session = server.createSession(transport)
            val done = Job()
            session.onClose {
                done.complete()
            }
            done.join()
        }
    }
}

private fun createHttpClient(): HttpClient = HttpClient(CIO) {
    defaultRequest {
        url("https://api.weather.gov")
        headers {
            append("Accept", "application/geo+json")
            append("User-Agent", "WeatherApiClient/1.0")
        }
        contentType(ContentType.Application.Json)
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            },
        )
    }
}

private fun Server.registerTools(httpClient: HttpClient) {
    // Register a tool to fetch weather alerts by state
    addTool(
        name = "get_alerts",
        description = "Get weather alerts for a US state. Input is a two-letter US state code (e.g. CA, NY)",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("state") {
                    put("type", "string")
                    put("description", "Two-letter US state code (e.g. CA, NY)")
                }
            },
            required = listOf("state"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val state = request.arguments?.get("state")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'state' parameter is required.")),
            )

        if (state.length != 2) {
            return@addTool CallToolResult(
                content = listOf(TextContent("Invalid state code: '$state'. Must be a two-letter US state code.")),
                isError = true,
            )
        }

        val stateCode = state.uppercase()

        httpClient.getAlerts(stateCode).fold(
            onSuccess = { alerts ->
                if (alerts.isEmpty()) {
                    CallToolResult(content = listOf(TextContent("No active alerts for $stateCode")))
                } else {
                    val alertsText = "Active alerts for $stateCode:\n\n${alerts.joinToString("\n---\n")}"
                    CallToolResult(content = listOf(TextContent(alertsText)))
                }
            },
            onFailure = { e ->
                CallToolResult(
                    content = listOf(TextContent("Failed to retrieve alerts data: ${e.message}")),
                    isError = true,
                )
            },
        )
    }

    // Register a tool to fetch weather forecast by latitude and longitude
    addTool(
        name = "get_forecast",
        description = "Get weather forecast for a location. Note: only US locations are supported by the NWS API.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("latitude") {
                    put("type", "number")
                    put("description", "Latitude of the location")
                    put("minimum", -90)
                    put("maximum", 90)
                }
                putJsonObject("longitude") {
                    put("type", "number")
                    put("description", "Longitude of the location")
                    put("minimum", -180)
                    put("maximum", 180)
                }
            },
            required = listOf("latitude", "longitude"),
        ),
        toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true),
    ) { request ->
        val latitude = request.arguments?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val longitude = request.arguments?.get("longitude")?.jsonPrimitive?.doubleOrNull
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'latitude' and 'longitude' parameters are required.")),
            )
        }

        httpClient.getForecast(latitude, longitude).fold(
            onSuccess = { periods ->
                if (periods.isEmpty()) {
                    CallToolResult(content = listOf(TextContent("No forecast periods available")))
                } else {
                    val forecastText = periods.joinToString("\n---\n")
                    CallToolResult(content = listOf(TextContent(forecastText)))
                }
            },
            onFailure = { _ ->
                CallToolResult(
                    content = listOf(
                        TextContent(
                            "Failed to retrieve grid point data for coordinates: $latitude, $longitude. " +
                                "This location may not be supported by the NWS API (only US locations are supported).",
                        ),
                    ),
                    isError = true,
                )
            },
        )
    }
}
