package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.Serializable

/**
 * Represents a method in the protocol, which can be predefined or custom.
 */
@Serializable // TODO: Custom serializer or use SerialName
public sealed interface Method {
    public val value: String

    /**
     * Enum of predefined methods supported by the protocol.
     */
    @Serializable
    public enum class Defined(override val value: String) : Method {
        Initialize("initialize"),
        Ping("ping"),
        ResourcesList("resources/list"),
        ResourcesTemplatesList("resources/templates/list"),
        ResourcesRead("resources/read"),
        ResourcesSubscribe("resources/subscribe"),
        ResourcesUnsubscribe("resources/unsubscribe"),
        PromptsList("prompts/list"),
        PromptsGet("prompts/get"),
        NotificationsCancelled("notifications/cancelled"),
        NotificationsInitialized("notifications/initialized"),
        NotificationsProgress("notifications/progress"),
        NotificationsMessage("notifications/message"),
        NotificationsResourcesUpdated("notifications/resources/updated"),
        NotificationsResourcesListChanged("notifications/resources/list_changed"),
        NotificationsToolsListChanged("notifications/tools/list_changed"),
        NotificationsRootsListChanged("notifications/roots/list_changed"),
        NotificationsPromptsListChanged("notifications/prompts/list_changed"),
        ToolsList("tools/list"),
        ToolsCall("tools/call"),
        LoggingSetLevel("logging/setLevel"),
        SamplingCreateMessage("sampling/createMessage"),
        CompletionComplete("completion/complete"),
        RootsList("roots/list"),
        ElicitationCreate("elicitation/create"),
    }

    /**
     * Represents a custom method defined by the user.
     */
    @Serializable
    public data class Custom(override val value: String) : Method
}
