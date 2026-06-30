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
 * Use [ClientCapabilities.Sampling] to configure tools/context sub-capabilities.
 * @property roots Present if the client supports listing roots.
 * @property elicitation Present if the client supports elicitation from the server.
 * Use [ClientCapabilities.Elicitation] to configure form/url mode sub-capabilities.
 * @property tasks Present if the client supports task-augmented requests, listing, or cancellation.
 * @property experimental Experimental, non-standard capabilities that the client supports.
 * Keys are capability names, values are capability-specific configuration objects.
 * @property extensions Optional extensions that the client supports.
 * Keys are extension identifiers (e.g., `"io.modelcontextprotocol/ui"`),
 * values are extension-specific settings objects.
 */
@Serializable
public data class ClientCapabilities(
    public val sampling: Sampling? = null,
    public val roots: Roots? = null,
    public val elicitation: Elicitation? = null,
    public val tasks: Tasks? = null,
    public val experimental: JsonObject? = null,
    public val extensions: Map<String, JsonObject>? = null,
) {

    /**
     * Source-compatibility constructor retaining the older `sampling: JsonObject?` /
     * `elicitation: JsonObject?` shapes. Any non-null [sampling] is converted to an
     * empty [Sampling], and any non-null [elicitation] is converted to an empty
     * [Elicitation] (sub-capabilities cannot be recovered from the old opaque
     * `JsonObject`).
     */
    @Deprecated(
        "ClientCapabilities.sampling and ClientCapabilities.elicitation are now typed. " +
            "Pass typed ClientCapabilities.Sampling? / ClientCapabilities.Elicitation? " +
            "instead of JsonObject?.",
        ReplaceWith(
            "ClientCapabilities(" +
                "sampling?.let { ClientCapabilities.Sampling() }, " +
                "roots, " +
                "elicitation?.let { ClientCapabilities.Elicitation() }, " +
                "tasks = null, " +
                "experimental = experimental, " +
                "extensions = extensions)",
        ),
        level = DeprecationLevel.WARNING,
    )
    public constructor(
        sampling: JsonObject?,
        roots: Roots? = null,
        elicitation: JsonObject? = null,
        experimental: JsonObject? = null,
        extensions: Map<String, JsonObject>? = null,
    ) : this(
        sampling = sampling?.let { Sampling() },
        roots = roots,
        elicitation = elicitation?.let { Elicitation() },
        tasks = null,
        experimental = experimental,
        extensions = extensions,
    )

    /**
     * sub-capabilities for sampling.
     *
     * @property context Present if the client supports context inclusion via
     * [CreateMessageRequestParams.includeContext] with values other than [IncludeContext.None].
     * Servers SHOULD avoid non-`none` values when this field is absent.
     * @property tools Present if the client supports tool use via
     * [CreateMessageRequestParams.tools] / [CreateMessageRequestParams.toolChoice].
     * Servers MUST NOT send `tools`/`toolChoice` when this field is absent.
     */
    @Serializable
    public data class Sampling(public val context: JsonObject? = null, public val tools: JsonObject? = null)

    /**
     * @property sampling convenience value to enable the base sampling capability with no sub-capabilities
     * @property elicitation convenience value to enable the elicitation capability with default form mode
     */
    public companion object {
        public val sampling: Sampling = Sampling()
        public val elicitation: Elicitation = Elicitation()
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

    /**
     * Sub-capabilities for elicitation.
     *
     * An empty [Elicitation] (both fields null) is equivalent to declaring `form` mode only.
     *
     * @property form Present if the client supports form-mode elicitation
     * (in-band structured data collection).
     * @property url  Present if the client supports url-mode elicitation
     * (out-of-band interaction via URL navigation).
     */
    @Serializable
    public data class Elicitation(public val form: JsonObject? = null, public val url: JsonObject? = null)

    /**
     * Sub-capabilities for tasks (client side).
     *
     * Declares which client-side requests can be augmented with task execution,
     * and whether the client supports listing and cancelling tasks.
     *
     * @property list     Present if the client supports the `tasks/list` operation.
     * @property cancel   Present if the client supports the `tasks/cancel` operation.
     * @property requests Present if the client supports task-augmented sampling and/or elicitation requests.
     */
    @Serializable
    public data class Tasks(
        public val list: JsonObject? = null,
        public val cancel: JsonObject? = null,
        public val requests: Requests? = null,
    ) {
        /**
         * Task-augmentable client-side request categories.
         *
         * @property sampling    Present if the client supports task-augmented `sampling/createMessage` requests.
         * @property elicitation Present if the client supports task-augmented `elicitation/create` requests.
         */
        @Serializable
        public data class Requests(
            public val sampling: Sampling? = null,
            public val elicitation: Elicitation? = null,
        ) {
            /**
             * @property createMessage Present if the client supports task-augmented `sampling/createMessage` requests.
             */
            @Serializable
            public data class Sampling(public val createMessage: JsonObject? = null)

            /**
             * @property create Present if the client supports task-augmented `elicitation/create` requests.
             */
            @Serializable
            public data class Elicitation(public val create: JsonObject? = null)
        }
    }
}

/**
 * Whether the client supports url-mode elicitation (out-of-band interaction via URL navigation).
 *
 * `false` when the client declared no elicitation capability at all, or only form mode (an empty
 * [ClientCapabilities.Elicitation] is treated as form mode only).
 */
public val ClientCapabilities.Elicitation?.supportsUrl: Boolean
    get() = this?.url != null

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
 * @property tasks Present if the server supports task-augmented requests, listing, or cancellation.
 * @property experimental Experimental, non-standard capabilities that the server supports.
 * @property extensions Optional extensions that the server supports.
 * Keys are extension identifiers (e.g., `"io.modelcontextprotocol/ui"`),
 * values are extension-specific settings objects.
 */
@Serializable
public data class ServerCapabilities(
    val tools: Tools? = null,
    val resources: Resources? = null,
    val prompts: Prompts? = null,
    val logging: JsonObject? = null,
    val completions: JsonObject? = null,
    val tasks: Tasks? = null,
    val experimental: JsonObject? = null,
    val extensions: Map<String, JsonObject>? = null,
) {

    /**
     * @property Logging convenience value to enable the logging capability
     * @property Completions convenience value to enable the completions capability
     */
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

    /**
     * Sub-capabilities for tasks (server side).
     *
     * Declares which server-side requests can be augmented with task execution,
     * and whether the server supports listing and cancelling tasks.
     *
     * @property list     Present if the server supports the `tasks/list` operation.
     * @property cancel   Present if the server supports the `tasks/cancel` operation.
     * @property requests Present if the server supports task-augmented tool call requests.
     */
    @Serializable
    public data class Tasks(
        public val list: JsonObject? = null,
        public val cancel: JsonObject? = null,
        public val requests: Requests? = null,
    ) {
        /**
         * Task-augmentable server-side request categories.
         *
         * @property tools Present if the server supports task-augmented `tools/call` requests.
         */
        @Serializable
        public data class Requests(public val tools: Tools? = null) {
            /**
             * @property call Present if the server supports task-augmented `tools/call` requests.
             */
            @Serializable
            public data class Tools(public val call: JsonObject? = null)
        }
    }
}
