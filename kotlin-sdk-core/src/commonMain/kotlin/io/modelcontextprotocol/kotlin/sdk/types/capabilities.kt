package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Describes an MCP (Model Context Protocol) implementation.
 *
 * This metadata helps clients identify and display information about servers or clients,
 * including their name, version, and optional branding elements like icons and website links.
 *
 * @property name The programmatic identifier for this implementation.
 * Intended for logical use and API identification. If [title] is not provided,
 * this should be used as a fallback display name.
 * @property version The version string of this implementation (e.g., "1.0.0", "2.1.3-beta").
 * @property title Optional human-readable display name for this implementation.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * If not provided, [name] should be used for display purposes.
 * @property websiteUrl Optional URL of the website for this implementation.
 * Can be used to provide documentation, support, or additional information.
 * @property icons Optional set of sized icons that clients can display in their user interface.
 * See [Icon] for supported formats and requirements.
 */
@Serializable
public data class Implementation(
    val name: String,
    val version: String,
    val title: String? = null,
    val websiteUrl: String? = null,
    val icons: List<Icon>? = null,
)

/**
 * Capabilities that a client may support.
 *
 * Known capabilities are defined here, but this is not a closed set: any client
 * can define its own additional capabilities through the [experimental] field.
 *
 * The presence of a capability object (non-null value) indicates that the client
 * supports that capability.
 *
 * @property sampling Present if the client supports sampling from an LLM.
 * @property roots Present if the client supports listing roots.
 * @property elicitation Present if the client supports elicitation from the server.
 * @property experimental Experimental, non-standard capabilities that the client supports.
 * Keys are capability names, values are capability-specific configuration objects.
 */
@Serializable
public data class ClientCapabilities(
    public val sampling: JsonObject? = null,
    public val roots: Roots? = null,
    public val elicitation: JsonObject? = null,
    public val experimental: JsonObject? = null,
) {

    public companion object {
        public val sampling: JsonObject = EmptyJsonObject
        public val elicitation: JsonObject = EmptyJsonObject
    }

    /**
     * Indicates that the client supports listing roots.
     *
     * Roots are the top-level directories or locations that the server can access.
     * When present (non-null), the server can query available roots and optionally
     * receive notifications when the roots list changes.
     *
     * @property listChanged Whether the client supports notifications for changes to the roots list.
     * If true, the client will send notifications when roots are added or removed.
     */
    @Serializable
    public data class Roots(val listChanged: Boolean? = null)
}

/**
 * Capabilities that a server may support.
 *
 * Known capabilities are defined here, but this is not a closed set: any server
 * can define its own additional capabilities through the [experimental] field.
 *
 * The presence of a capability object (non-null value) indicates that the server
 * supports that capability.
 *
 * @property tools Present if the server offers any tools to call.
 * @property resources Present if the server offers any resources to read.
 * @property prompts Present if the server offers any prompt templates.
 * @property logging Present if the server supports sending log messages to the client.
 * @property completions Present if the server supports argument autocompletion suggestions.
 * Keys are capability names, values are capability-specific configuration objects.
 * @property experimental Experimental, non-standard capabilities that the server supports.
 */
@Serializable
public data class ServerCapabilities(
    val tools: Tools? = null,
    val resources: Resources? = null,
    val prompts: Prompts? = null,
    val logging: JsonObject? = null,
    val completions: JsonObject? = null,
    val experimental: JsonObject? = null,
) {

    public companion object {
        public val Logging: JsonObject = EmptyJsonObject
        public val Completions: JsonObject = EmptyJsonObject
    }

    /**
     * Indicates that the server offers tools to call.
     *
     * When present (non-null), clients can list and invoke tools (functions, actions, etc.)
     * provided by the server.
     *
     * @property listChanged Whether this server supports notifications for changes to the tool list.
     * If true, the server will send notifications when tools are added, modified, or removed.
     */
    @Serializable
    public data class Tools(val listChanged: Boolean? = null)

    /**
     * Indicates that the server offers resources to read.
     *
     * When present (non-null), clients can list and read resources (files, data sources, etc.)
     * provided by the server.
     *
     * @property listChanged Whether this server supports notifications for changes to the resource list.
     * If true, the server will send notifications when resources are added or removed.
     * @property subscribe Whether this server supports subscribing to resource updates.
     * If true, clients can subscribe to receive notifications when specific
     * resources are modified.
     */
    @Serializable
    public data class Resources(val listChanged: Boolean? = null, val subscribe: Boolean? = null)

    /**
     * Indicates that the server offers prompt templates.
     *
     * When present (non-null), clients can list and invoke prompts provided by the server.
     *
     * @property listChanged Whether this server supports notifications for changes to the prompt list.
     * If true, the server will send notifications when prompts are added, modified, or removed.
     */
    @Serializable
    public data class Prompts(val listChanged: Boolean? = null)
}
