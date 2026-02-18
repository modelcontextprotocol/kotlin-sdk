package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * DSL builder for constructing [ClientCapabilities] instances.
 *
 * This builder is used within [InitializeRequestBuilder] to configure client capabilities.
 * All capabilities are optional - the presence of a capability indicates support for that feature.
 *
 * ## Available Functions (all optional)
 * - [sampling] - Indicates support for sampling from an LLM
 * - [roots] - Indicates support for listing roots
 * - [elicitation] - Indicates support for elicitation from the server
 * - [experimental] - Defines experimental, non-standard capabilities
 *
 * Example usage within [InitializeRequest][InitializeRequest]:
 * ```kotlin
 * val request = InitializeRequest {
 *     protocolVersion = "1.0"
 *     capabilities {
 *         sampling(ClientCapabilities.sampling)
 *         roots(listChanged = true)
 *         experimental {
 *             put("customFeature", JsonPrimitive(true))
 *         }
 *     }
 *     info("MyClient", "1.0.0")
 * }
 * ```
 *
 * @see ClientCapabilities
 * @see InitializeRequestBuilder.capabilities
 */
@McpDsl
public class ClientCapabilitiesBuilder @PublishedApi internal constructor() {
    private var sampling: JsonObject? = null
    private var roots: ClientCapabilities.Roots? = null
    private var elicitation: JsonObject? = null
    private var experimental: JsonObject? = null

    /**
     * Indicates that the client supports sampling from an LLM.
     *
     * Use [ClientCapabilities.sampling] for default empty configuration.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     sampling(ClientCapabilities.sampling)
     * }
     * ```
     *
     * @param value The sampling capability configuration
     */
    public fun sampling(value: JsonObject) {
        this.sampling = value
    }

    /**
     * Indicates that the client supports sampling from an LLM with custom configuration.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     sampling {
     *         put("temperature", JsonPrimitive(0.7))
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the sampling configuration
     */
    public fun sampling(block: JsonObjectBuilder.() -> Unit): Unit = sampling(buildJsonObject(block))

    /**
     * Indicates that the client supports listing roots.
     *
     * Example with listChanged notification:
     * ```kotlin
     * capabilities {
     *     roots(listChanged = true)
     * }
     * ```
     *
     * Example without listChanged:
     * ```kotlin
     * capabilities {
     *     roots()
     * }
     * ```
     *
     * @param listChanged Whether the client will emit notifications when the list of roots changes
     */
    public fun roots(listChanged: Boolean? = null) {
        this.roots = ClientCapabilities.Roots(listChanged)
    }

    /**
     * Indicates that the client supports elicitation from the server.
     *
     * Use [ClientCapabilities.elicitation] for default empty configuration.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     elicitation(ClientCapabilities.elicitation)
     * }
     * ```
     *
     * @param value The elicitation capability configuration
     */
    public fun elicitation(value: JsonObject) {
        this.elicitation = value
    }

    /**
     * Indicates that the client supports elicitation from the server with custom configuration.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     elicitation {
     *         put("mode", JsonPrimitive("interactive"))
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the elicitation configuration
     */
    public fun elicitation(block: JsonObjectBuilder.() -> Unit): Unit = elicitation(buildJsonObject(block))

    /**
     * Defines experimental, non-standard capabilities that the client supports.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     experimental(buildJsonObject {
     *         put("customFeature", JsonPrimitive(true))
     *         put("version", JsonPrimitive("1.0"))
     *     })
     * }
     * ```
     *
     * @param value The experimental capabilities configuration
     */
    public fun experimental(value: JsonObject) {
        this.experimental = value
    }

    /**
     * Defines experimental, non-standard capabilities that the client supports using a DSL builder.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     experimental {
     *         put("customFeature", JsonPrimitive(true))
     *         put("beta", JsonObject(mapOf(
     *             "enabled" to JsonPrimitive(true),
     *             "version" to JsonPrimitive("2.0")
     *         )))
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the experimental capabilities configuration
     */
    public fun experimental(block: JsonObjectBuilder.() -> Unit): Unit = experimental(buildJsonObject(block))

    @PublishedApi
    internal fun build(): ClientCapabilities = ClientCapabilities(
        sampling = sampling,
        roots = roots,
        elicitation = elicitation,
        experimental = experimental,
    )
}
