package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.typeOf

/**
 * Extension function to register tools from class methods annotated with [McpTool].
 * This function will scan the provided class for methods annotated with [McpTool] and register them as tools.
 *
 * @param instance The instance of the class containing the annotated methods.
 * @param T The type of the class.
 */
public inline fun <reified T : Any> Server.registerAnnotatedTools(instance: T) {
    val kClass = T::class
    
    kClass.members
        .filterIsInstance<KFunction<*>>()
        .filter { it.hasAnnotation<McpTool>() }
        .forEach { function ->
            val annotation = function.findAnnotation<McpTool>()!!
            registerToolFromAnnotatedFunction(instance, function, annotation)
        }
}

/**
 * Extension function to register a single tool from an annotated function.
 *
 * @param instance The instance of the class containing the annotated method.
 * @param function The function to register as a tool.
 * @param annotation The [McpTool] annotation.
 */
public fun <T : Any> Server.registerToolFromAnnotatedFunction(
    instance: T,
    function: KFunction<*>,
    annotation: McpTool
) {
    val name = if (annotation.name.isEmpty()) function.name else annotation.name
    val description = annotation.description
    
    // Build the input schema
    val properties = buildJsonObject {
        function.valueParameters.forEach { param ->
            val paramAnnotation = param.findAnnotation<McpParam>()
            val paramName = param.name ?: "param${param.index}"
            
            putJsonObject(paramName) {
                val type = when {
                    paramAnnotation != null && paramAnnotation.type.isNotEmpty() -> paramAnnotation.type
                    // Infer type from Kotlin parameter type
                    else -> inferJsonSchemaType(param.type)
                }
                
                put("type", type)
                
                if (paramAnnotation != null && paramAnnotation.description.isNotEmpty()) {
                    put("description", paramAnnotation.description)
                }
            }
        }
    }
    
    // Determine required parameters
    val required = if (annotation.required.isNotEmpty()) {
        annotation.required.toList()
    } else {
        function.valueParameters
            .filter { param ->
                val paramAnnotation = param.findAnnotation<McpParam>()
                paramAnnotation?.required != false && !param.isOptional
            }
            .map { it.name ?: "param${it.index}" }
    }
    
    // Create tool input schema
    val inputSchema = Tool.Input(
        properties = properties,
        required = required
    )
    
    // Add the tool with a handler that calls the annotated function
    addTool(
        name = name,
        description = description,
        inputSchema = inputSchema
    ) { request ->
        try {
            // Since we can't use reflection to call the function in multiplatform code,
            // we'll use a more direct approach based on the specific parameter requirements
            
            // Get all arguments from the request
            val args = function.valueParameters.map { param ->
                val paramName = param.name ?: "param${param.index}"
                val jsonValue = request.arguments[paramName]
                convertJsonValueToKotlinType(jsonValue, param.type)
            }
            
            // Invoke the function directly on the instance using the collected arguments
            val result = when (args.size) {
                0 -> function.call(instance)
                1 -> function.call(instance, args[0])
                2 -> function.call(instance, args[0], args[1])
                3 -> function.call(instance, args[0], args[1], args[2])
                4 -> function.call(instance, args[0], args[1], args[2], args[3])
                5 -> function.call(instance, args[0], args[1], args[2], args[3], args[4])
                else -> throw IllegalArgumentException("Functions with more than 5 parameters are not supported")
            }
            
            // Handle the result
            when (result) {
                is CallToolResult -> result
                is String -> CallToolResult(content = listOf(TextContent(result)))
                is List<*> -> {
                    val textContent = result.filterIsInstance<String>().map { TextContent(it) }
                    CallToolResult(content = textContent)
                }
                null -> CallToolResult(content = listOf(TextContent("Operation completed successfully")))
                else -> CallToolResult(content = listOf(TextContent(result.toString())))
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error executing tool: ${e.message}")),
                isError = true
            )
        }
    }
}

/**
 * Infers JSON Schema type from Kotlin type.
 */
private fun inferJsonSchemaType(type: KType): String {
    return when (type.classifier) {
        String::class -> "string"
        Int::class, Long::class, Short::class, Byte::class -> "integer"
        Float::class, Double::class -> "number"
        Boolean::class -> "boolean"
        List::class, Array::class, Set::class -> "array"
        Map::class -> "object"
        else -> "string" // Default to string for complex types
    }
}

/**
 * Converts a JSON value to the expected Kotlin type.
 */
private fun convertJsonValueToKotlinType(jsonValue: Any?, targetType: KType): Any? {
    if (jsonValue == null) return null
    
    // Handle JsonPrimitive
    if (jsonValue is JsonPrimitive) {
        return when (targetType.classifier) {
            String::class -> jsonValue.content
            Int::class -> jsonValue.content.toIntOrNull()
            Long::class -> jsonValue.content.toLongOrNull()
            Double::class -> jsonValue.content.toDoubleOrNull()
            Float::class -> jsonValue.content.toFloatOrNull()
            Boolean::class -> jsonValue.content.toBoolean()
            else -> jsonValue.content
        }
    }
    
    // For now, just return the raw JSON value for complex types
    return jsonValue
}