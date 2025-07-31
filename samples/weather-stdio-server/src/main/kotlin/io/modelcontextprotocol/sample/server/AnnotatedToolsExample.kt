package io.modelcontextprotocol.sample.server

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.McpParam
import io.modelcontextprotocol.kotlin.sdk.server.McpTool
import io.modelcontextprotocol.kotlin.sdk.server.registerAnnotatedTools

/**
 * Example class demonstrating the use of McpTool annotations.
 */
class WeatherToolsAnnotated(private val httpClient: HttpClient) {
    
    /**
     * Gets weather alerts for a specified US state using the @McpTool annotation.
     */
    @McpTool(
        name = "get_alerts",
        description = "Get weather alerts for a US state"
    )
    suspend fun getAlerts(
        @McpParam(
            description = "Two-letter US state code (e.g. CA, NY)",
            type = "string"
        ) state: String
    ): CallToolResult {
        if (state.isEmpty()) {
            return CallToolResult(
                content = listOf(TextContent("The 'state' parameter is required."))
            )
        }

        val alerts = httpClient.getAlerts(state)
        return CallToolResult(content = alerts.map { TextContent(it) })
    }

    /**
     * Gets weather forecast for specified coordinates using the @McpTool annotation.
     */
    @McpTool(
        name = "get_forecast",
        description = "Get weather forecast for a specific latitude/longitude"
    )
    suspend fun getForecast(
        @McpParam(description = "The latitude coordinate") latitude: Double,
        @McpParam(description = "The longitude coordinate") longitude: Double
    ): CallToolResult {
        val forecast = httpClient.getForecast(latitude, longitude)
        return CallToolResult(content = forecast.map { TextContent(it) })
    }
    
    /**
     * Gets brief weather summary using the @McpTool annotation with default name.
     */
    @McpTool(
        description = "Get a brief weather summary for a location"
    )
    suspend fun getWeatherSummary(
        @McpParam(description = "City name") city: String,
        @McpParam(description = "Temperature unit (celsius/fahrenheit)", required = false) unit: String = "celsius"
    ): String {
        return "Weather summary for $city: Sunny, 25° $unit"
    }
}