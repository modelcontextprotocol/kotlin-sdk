package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.types.ProtocolEra.Companion.BOTH
import io.modelcontextprotocol.kotlin.sdk.types.ProtocolEra.Companion.LEGACY_ONLY
import io.modelcontextprotocol.kotlin.sdk.types.ProtocolEra.Companion.MODERN_ONLY
import kotlinx.serialization.Serializable

/**
 * Represents a method in the protocol, which can be predefined or custom.
 */
@Serializable(with = MethodSerializer::class)
public sealed interface Method {
    /** The string representation of this method name. */
    public val value: String

    /**
     * Enum of predefined methods supported by the protocol.
     *
     * Each constant declares the protocol lifecycles it exists in, so the set of methods a revision
     * removed is a property of the table rather than a list kept somewhere else: a constant cannot
     * be added without deciding where it is reachable. Read it through
     * [isAvailableIn][io.modelcontextprotocol.kotlin.sdk.types.isAvailableIn].
     *
     * Direction is not expressible here and is enforced at the transport instead. On the
     * request-scoped lifecycle `notifications/cancelled` travels only over stdio — over HTTP,
     * closing the response stream *is* cancellation — while `notifications/progress` and
     * `notifications/message` flow server-to-client on the originating request's response stream
     * only. The three `list_changed` notifications keep their types but have no request-scoped
     * delivery channel until `subscriptions/listen` lands, so a request-scoped server emits none.
     */
    @Serializable
    public enum class Defined(override val value: String, internal val eras: Set<ProtocolEra>) : Method {
        Initialize("initialize", LEGACY_ONLY),
        ServerDiscover("server/discover", MODERN_ONLY),
        Ping("ping", LEGACY_ONLY),
        ResourcesList("resources/list", BOTH),
        ResourcesTemplatesList("resources/templates/list", BOTH),
        ResourcesRead("resources/read", BOTH),
        ResourcesSubscribe("resources/subscribe", LEGACY_ONLY),
        ResourcesUnsubscribe("resources/unsubscribe", LEGACY_ONLY),
        PromptsList("prompts/list", BOTH),
        PromptsGet("prompts/get", BOTH),
        NotificationsCancelled("notifications/cancelled", BOTH),
        NotificationsInitialized("notifications/initialized", LEGACY_ONLY),
        NotificationsProgress("notifications/progress", BOTH),
        NotificationsMessage("notifications/message", BOTH),
        NotificationsResourcesUpdated("notifications/resources/updated", LEGACY_ONLY),
        NotificationsResourcesListChanged("notifications/resources/list_changed", BOTH),
        NotificationsToolsListChanged("notifications/tools/list_changed", BOTH),
        NotificationsRootsListChanged("notifications/roots/list_changed", LEGACY_ONLY),
        NotificationsPromptsListChanged("notifications/prompts/list_changed", BOTH),
        NotificationsElicitationComplete("notifications/elicitation/complete", LEGACY_ONLY),

        // Tasks moved out of core into an extension, which needs the extensions capability map.
        NotificationsTasksStatus("notifications/tasks/status", LEGACY_ONLY),
        ToolsList("tools/list", BOTH),
        ToolsCall("tools/call", BOTH),
        LoggingSetLevel("logging/setLevel", LEGACY_ONLY),

        // Server-to-client requests, replaced wholesale by multi-round-trip requests.
        SamplingCreateMessage("sampling/createMessage", LEGACY_ONLY),
        CompletionComplete("completion/complete", BOTH),
        RootsList("roots/list", LEGACY_ONLY),
        ElicitationCreate("elicitation/create", LEGACY_ONLY),
        TasksGet("tasks/get", LEGACY_ONLY),
        TasksResult("tasks/result", LEGACY_ONLY),
        TasksList("tasks/list", LEGACY_ONLY),
        TasksCancel("tasks/cancel", LEGACY_ONLY),
    }

    /**
     * Represents a custom method defined by the user.
     */
    @Serializable
    public data class Custom(override val value: String) : Method

    public companion object {
        private val definedByValue: Map<String, Defined> by lazy {
            Defined.entries.associateBy { it.value }
        }

        /** Resolves a wire method string to a [Defined] entry when known, [Custom] otherwise. */
        internal fun from(value: String): Method = definedByValue[value] ?: Custom(value)
    }
}
