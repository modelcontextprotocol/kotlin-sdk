package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * Creates an [ElicitRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [message][ElicitRequestBuilder.message] - The message to present to the user
 * - [requestedSchema][ElicitRequestBuilder.requestedSchema] - Schema defining the structure of requested data
 *
 * ## Optional
 * - [meta][ElicitRequestBuilder.meta] - Metadata for the request
 *
 * Example requesting user information:
 * ```kotlin
 * val request = ElicitRequest {
 *     message = "Please provide your contact information"
 *     requestedSchema {
 *         properties {
 *             put("email", JsonObject(mapOf(
 *                 "type" to JsonPrimitive("string"),
 *                 "description" to JsonPrimitive("Your email address")
 *             )))
 *             put("name", JsonObject(mapOf(
 *                 "type" to JsonPrimitive("string")
 *             )))
 *         }
 *         required = listOf("email")
 *     }
 * }
 * ```
 *
 * Example with simple text input:
 * ```kotlin
 * val request = ElicitRequest {
 *     message = "Enter a project name"
 *     requestedSchema {
 *         properties {
 *             put("projectName", JsonObject(mapOf(
 *                 "type" to JsonPrimitive("string"),
 *                 "description" to JsonPrimitive("Name for the new project")
 *             )))
 *         }
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the elicitation request
 * @return A configured [ElicitRequest] instance
 * @see ElicitRequestBuilder
 * @see ElicitRequest
 */
@ExperimentalMcpApi
public inline operator fun ElicitRequest.Companion.invoke(block: ElicitRequestBuilder.() -> Unit): ElicitRequest =
    ElicitRequestBuilder().apply(block).build()

/**
 * DSL builder for constructing [ElicitRequest] instances.
 *
 * This builder is used to create requests that prompt users for structured input
 * through forms or dialogs presented by the client.
 *
 * ## Required
 * - [message] - The message to present to the user explaining what is being requested
 * - [requestedSchema] - Schema defining the structure of the data to collect
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see ElicitRequest
 */
@McpDsl
public class ElicitRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The message to present to the user. This should clearly explain what information
     * is being requested and why. This is a required field.
     */
    public var message: String? = null

    private var requestedSchema: ElicitRequestParams.RequestedSchema? = null

    /**
     * Sets the requested schema directly.
     *
     * Example:
     * ```kotlin
     * ElicitRequest {
     *     message = "Enter details"
     *     requestedSchema(ElicitRequestParams.RequestedSchema(
     *         properties = buildJsonObject {
     *             put("name", JsonObject(mapOf("type" to JsonPrimitive("string"))))
     *         }
     *     ))
     * }
     * ```
     *
     * @param schema The [ElicitRequestParams.RequestedSchema] instance
     */
    public fun requestedSchema(schema: ElicitRequestParams.RequestedSchema) {
        requestedSchema = schema
    }

    /**
     * Sets the requested schema using a DSL builder.
     *
     * This is the recommended way to define schemas. The schema defines the structure
     * of data to be collected from the user, supporting only top-level primitive properties.
     *
     * Example:
     * ```kotlin
     * ElicitRequest {
     *     message = "Configure settings"
     *     requestedSchema {
     *         properties {
     *             put("enabled", JsonObject(mapOf(
     *                 "type" to JsonPrimitive("boolean"),
     *                 "description" to JsonPrimitive("Enable feature")
     *             )))
     *             put("apiKey", JsonObject(mapOf(
     *                 "type" to JsonPrimitive("string")
     *             )))
     *         }
     *         required = listOf("apiKey")
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the schema
     * @see ElicitRequestedSchemaBuilder
     */
    public fun requestedSchema(block: ElicitRequestedSchemaBuilder.() -> Unit) {
        requestedSchema = ElicitRequestedSchemaBuilder().apply(block).build()
    }

    @PublishedApi
    override fun build(): ElicitRequest {
        val message = requireNotNull(message) {
            "Missing required field 'message'. Example: message = \"Please enter your name\""
        }
        val requestedSchema = requireNotNull(requestedSchema) {
            "Missing required field 'requestedSchema'. Use requestedSchema { properties { ... } }"
        }

        val params = ElicitRequestParams(message = message, requestedSchema = requestedSchema, meta = meta)
        return ElicitRequest(params)
    }
}

/**
 * DSL builder for constructing [ElicitRequestParams.RequestedSchema] instances.
 *
 * Defines the JSON Schema structure for data to be collected from the user.
 * Only supports top-level primitive properties.
 *
 * ## Required
 * - [properties] - Schema definitions for each field
 *
 * ## Optional
 * - [required] - List of required property names
 *
 * Example:
 * ```kotlin
 * requestedSchema {
 *     properties {
 *         put("username", JsonObject(mapOf(
 *             "type" to JsonPrimitive("string"),
 *             "description" to JsonPrimitive("Your username")
 *         )))
 *     }
 *     required = listOf("username")
 * }
 * ```
 *
 * @see ElicitRequestParams.RequestedSchema
 */
@McpDsl
public class ElicitRequestedSchemaBuilder @PublishedApi internal constructor() {
    private var properties: JsonObject? = null

    /**
     * List of required property names. If null, all fields are optional.
     */
    public var required: List<String>? = null

    /**
     * Sets the schema properties directly.
     *
     * @param properties JsonObject containing property schemas
     */
    public fun properties(properties: JsonObject) {
        this.properties = properties
    }

    /**
     * Sets the schema properties using a DSL builder.
     *
     * Example:
     * ```kotlin
     * properties {
     *     put("email", JsonObject(mapOf(
     *         "type" to JsonPrimitive("string")
     *     )))
     * }
     * ```
     *
     * @param block Lambda for building the properties
     */
    public fun properties(block: JsonObjectBuilder.() -> Unit): Unit = properties(buildJsonObject(block))

    @PublishedApi
    internal fun build(): ElicitRequestParams.RequestedSchema {
        val properties = requireNotNull(properties) {
            "Missing required field 'properties'. Use properties { put(\"fieldName\", schema) }"
        }
        return ElicitRequestParams.RequestedSchema(properties, required)
    }
}
