package io.modelcontextprotocol.kotlin.sdk.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.reflect.KClass

/**
 * Annotation to define an MCP tool with simplified syntax.
 * 
 * Use this annotation on functions that should be registered as tools in the MCP server.
 * 
 * Example:
 * ```kotlin
 * @McpTool(
 *     name = "get_forecast",
 *     description = "Get weather forecast for a specific latitude/longitude"
 * )
 * fun getForecastTool(latitude: Double, longitude: Double): CallToolResult {
 *     // implementation
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class McpTool(
    val name: String = "",
    val description: String = "",
    val required: Array<String> = [],
) 

/**
 * Annotation to define a parameter for an MCP tool.
 * 
 * Use this annotation on function parameters to specify additional metadata for tool input schema.
 * 
 * Example:
 * ```kotlin
 * @McpTool(name = "get_forecast", description = "Get weather forecast")
 * fun getForecastTool(
 *     @McpParam(description = "The latitude coordinate", type = "number") latitude: Double,
 *     @McpParam(description = "The longitude coordinate", type = "number") longitude: Double
 * ): CallToolResult {
 *     // implementation
 * }
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
public annotation class McpParam(
    val description: String = "",
    val type: String = "", // Can be overridden, otherwise inferred from Kotlin type
    val required: Boolean = true
)