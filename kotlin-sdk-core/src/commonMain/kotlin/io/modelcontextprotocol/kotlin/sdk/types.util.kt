package io.modelcontextprotocol.kotlin.sdk

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification.SetLevelRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

private val logger = KotlinLogging.logger {}

@Deprecated(
    message = "This object will be removed in future versions",
    level = DeprecationLevel.WARNING,
)
internal object ErrorCodeSerializer : KSerializer<ErrorCode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.ErrorCode", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ErrorCode) {
        encoder.encodeInt(value.code)
    }

    override fun deserialize(decoder: Decoder): ErrorCode {
        val decodedInt = decoder.decodeInt()
        return ErrorCode.Defined.entries.firstOrNull { it.code == decodedInt }
            ?: ErrorCode.Unknown(decodedInt)
    }
}

@Deprecated(
    message = "Use `MethodSerializer` instead",
    replaceWith = ReplaceWith("MethodSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object RequestMethodSerializer : KSerializer<Method> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.Method", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Method) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Method {
        val decodedString = decoder.decodeString()
        return Method.Defined.entries.firstOrNull { it.value == decodedString }
            ?: Method.Custom(decodedString)
    }
}

@Deprecated(
    message = "This object will be removed in future versions",
    level = DeprecationLevel.WARNING,
)
internal object StopReasonSerializer : KSerializer<StopReason> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.StopReason", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StopReason) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): StopReason {
        val decodedString = decoder.decodeString()
        return when (decodedString) {
            StopReason.StopSequence.value -> StopReason.StopSequence
            StopReason.MaxTokens.value -> StopReason.MaxTokens
            StopReason.EndTurn.value -> StopReason.EndTurn
            else -> StopReason.Other(decodedString)
        }
    }
}

@Deprecated(
    message = "Use `ReferencePolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ReferencePolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object ReferencePolymorphicSerializer : JsonContentPolymorphicSerializer<Reference>(Reference::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Reference> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ResourceTemplateReference.TYPE -> ResourceTemplateReference.serializer()
            PromptReference.TYPE -> PromptReference.serializer()
            else -> UnknownReference.serializer()
        }
}

@Deprecated(
    message = "Use `ContentBlockPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ContentBlockPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object PromptMessageContentPolymorphicSerializer :
    JsonContentPolymorphicSerializer<PromptMessageContent>(PromptMessageContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PromptMessageContent> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ImageContent.TYPE -> ImageContent.serializer()
            TextContent.TYPE -> TextContent.serializer()
            EmbeddedResource.TYPE -> EmbeddedResource.serializer()
            AudioContent.TYPE -> AudioContent.serializer()
            else -> UnknownContent.serializer()
        }
}

@Deprecated(
    message = "Use `MediaContentPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("MediaContentPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object PromptMessageContentMultimodalPolymorphicSerializer :
    JsonContentPolymorphicSerializer<PromptMessageContentMultimodal>(PromptMessageContentMultimodal::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PromptMessageContentMultimodal> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ImageContent.TYPE -> ImageContent.serializer()
            TextContent.TYPE -> TextContent.serializer()
            AudioContent.TYPE -> AudioContent.serializer()
            else -> UnknownContent.serializer()
        }
}

@Deprecated(
    message = "Use `ResourceContentsPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ResourceContentsPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object ResourceContentsPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ResourceContents>(ResourceContents::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResourceContents> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("text") -> TextResourceContents.serializer()
            jsonObject.contains("blob") -> BlobResourceContents.serializer()
            else -> UnknownResourceContents.serializer()
        }
    }
}

@Deprecated(
    message = "This method will be removed in future versions",
    level = DeprecationLevel.WARNING,
)
internal fun selectRequestDeserializer(method: String): DeserializationStrategy<Request> {
    selectClientRequestDeserializer(method)?.let { return it }
    selectServerRequestDeserializer(method)?.let { return it }
    return CustomRequest.serializer()
}

@Deprecated(
    message = "Use `selectClientRequestDeserializer` instead",
    replaceWith = ReplaceWith("selectClientRequestDeserializer"),
    level = DeprecationLevel.WARNING,
)
internal fun selectClientRequestDeserializer(method: String): DeserializationStrategy<ClientRequest>? = when (method) {
    Method.Defined.Ping.value -> PingRequest.serializer()
    Method.Defined.Initialize.value -> InitializeRequest.serializer()
    Method.Defined.CompletionComplete.value -> CompleteRequest.serializer()
    Method.Defined.LoggingSetLevel.value -> SetLevelRequest.serializer()
    Method.Defined.PromptsGet.value -> GetPromptRequest.serializer()
    Method.Defined.PromptsList.value -> ListPromptsRequest.serializer()
    Method.Defined.ResourcesList.value -> ListResourcesRequest.serializer()
    Method.Defined.ResourcesTemplatesList.value -> ListResourceTemplatesRequest.serializer()
    Method.Defined.ResourcesRead.value -> ReadResourceRequest.serializer()
    Method.Defined.ResourcesSubscribe.value -> SubscribeRequest.serializer()
    Method.Defined.ResourcesUnsubscribe.value -> UnsubscribeRequest.serializer()
    Method.Defined.ToolsCall.value -> CallToolRequest.serializer()
    Method.Defined.ToolsList.value -> ListToolsRequest.serializer()
    else -> null
}

@Deprecated(
    message = "Use `selectClientNotificationDeserializer` instead",
    replaceWith = ReplaceWith("selectClientNotificationDeserializer"),
    level = DeprecationLevel.WARNING,
)
private fun selectClientNotificationDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification>? =
    when (element.jsonObject.getValue("method").jsonPrimitive.content) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsInitialized.value -> InitializedNotification.serializer()
        Method.Defined.NotificationsRootsListChanged.value -> RootsListChangedNotification.serializer()
        else -> null
    }

@Deprecated(
    message = "Use `ClientNotificationPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ClientNotificationPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object ClientNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientNotification>(ClientNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification> =
        selectClientNotificationDeserializer(element)
            ?: UnknownMethodRequestOrNotification.serializer()
}

@Deprecated(
    message = "Use `selectServerRequestDeserializer` instead",
    replaceWith = ReplaceWith("selectServerRequestDeserializer"),
    level = DeprecationLevel.WARNING,
)
internal fun selectServerRequestDeserializer(method: String): DeserializationStrategy<ServerRequest>? = when (method) {
    Method.Defined.Ping.value -> PingRequest.serializer()
    Method.Defined.SamplingCreateMessage.value -> CreateMessageRequest.serializer()
    Method.Defined.RootsList.value -> ListRootsRequest.serializer()
    else -> null
}

@Deprecated(
    message = "Use `selectServerNotificationDeserializer` instead",
    replaceWith = ReplaceWith("selectServerNotificationDeserializer"),
    level = DeprecationLevel.WARNING,
)
internal fun selectServerNotificationDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification>? =
    when (element.jsonObject.getValue("method").jsonPrimitive.content) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsMessage.value -> LoggingMessageNotification.serializer()
        Method.Defined.NotificationsResourcesUpdated.value -> ResourceUpdatedNotification.serializer()
        Method.Defined.NotificationsResourcesListChanged.value -> ResourceListChangedNotification.serializer()
        Method.Defined.NotificationsToolsListChanged.value -> ToolListChangedNotification.serializer()
        Method.Defined.NotificationsPromptsListChanged.value -> PromptListChangedNotification.serializer()
        else -> null
    }

@Deprecated(
    message = "Use `ServerNotificationPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ServerNotificationPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object ServerNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerNotification>(ServerNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification> =
        selectServerNotificationDeserializer(element)
            ?: UnknownMethodRequestOrNotification.serializer()
}

@Deprecated(
    message = "Use `NotificationPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("NotificationPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object NotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Notification>(Notification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Notification> =
        selectClientNotificationDeserializer(element)
            ?: selectServerNotificationDeserializer(element)
            ?: UnknownMethodRequestOrNotification.serializer()
}

@Deprecated(
    message = "Use `RequestPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("RequestPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object RequestPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Request>(Request::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Request> {
        val method = element.jsonObject.getOrElse("method") { null }?.jsonPrimitive?.content ?: run {
            logger.error { "No method in $element" }
            error("No method in $element")
        }

        return selectClientRequestDeserializer(method)
            ?: selectServerRequestDeserializer(method)
            ?: UnknownMethodRequestOrNotification.serializer()
    }
}

@Deprecated(
    message = "Use `selectServerResultDeserializer` instead",
    replaceWith = ReplaceWith("selectServerResultDeserializer"),
    level = DeprecationLevel.WARNING,
)
private fun selectServerResultDeserializer(element: JsonElement): DeserializationStrategy<ServerResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("tools") -> ListToolsResult.serializer()
        jsonObject.contains("resources") -> ListResourcesResult.serializer()
        jsonObject.contains("resourceTemplates") -> ListResourceTemplatesResult.serializer()
        jsonObject.contains("prompts") -> ListPromptsResult.serializer()
        jsonObject.contains("capabilities") -> InitializeResult.serializer()
        jsonObject.contains("description") -> GetPromptResult.serializer()
        jsonObject.contains("completion") -> CompleteResult.serializer()
        jsonObject.contains("toolResult") -> CompatibilityCallToolResult.serializer()
        jsonObject.contains("contents") -> ReadResourceResult.serializer()
        jsonObject.contains("content") -> CallToolResult.serializer()
        else -> null
    }
}

@Deprecated(
    message = "Use `selectClientResultDeserializer` instead",
    replaceWith = ReplaceWith("selectClientResultDeserializer"),
    level = DeprecationLevel.WARNING,
)
private fun selectClientResultDeserializer(element: JsonElement): DeserializationStrategy<ClientResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("model") -> CreateMessageResult.serializer()
        jsonObject.contains("roots") -> ListRootsResult.serializer()
        jsonObject.contains("action") -> CreateElicitationResult.serializer()
        else -> null
    }
}

@Deprecated(
    message = "Use `ServerResultPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ServerResultPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object ServerResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerResult>(ServerResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerResult> =
        selectServerResultDeserializer(element)
            ?: EmptyRequestResult.serializer()
}

@Deprecated(
    message = "Use `ClientResultPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("ClientResultPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object ClientResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientResult>(ClientResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientResult> =
        selectClientResultDeserializer(element)
            ?: EmptyRequestResult.serializer()
}

@Deprecated(
    message = "Use `RequestResultPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("RequestResultPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object RequestResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<RequestResult>(RequestResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestResult> =
        selectClientResultDeserializer(element)
            ?: selectServerResultDeserializer(element)
            ?: EmptyRequestResult.serializer()
}

@Deprecated(
    message = "Use `JSONRPCMessagePolymorphicSerializer` instead",
    replaceWith = ReplaceWith("JSONRPCMessagePolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
internal object JSONRPCMessagePolymorphicSerializer :
    JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("message") -> JSONRPCError.serializer()
            !jsonObject.contains("method") -> JSONRPCResponse.serializer()
            jsonObject.contains("id") -> JSONRPCRequest.serializer()
            else -> JSONRPCNotification.serializer()
        }
    }
}

@Deprecated(
    message = "Use `io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject` instead",
    replaceWith = ReplaceWith("EmptyJsonObject", "io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject"),
    level = DeprecationLevel.WARNING,
)
public val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

@Deprecated(
    message = "Use `RequestIdPolymorphicSerializer` instead",
    replaceWith = ReplaceWith("RequestIdPolymorphicSerializer"),
    level = DeprecationLevel.WARNING,
)
public class RequestIdSerializer : KSerializer<RequestId> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RequestId")

    override fun deserialize(decoder: Decoder): RequestId {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only deserialize JSON")
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> RequestId.StringId(element.content)
                element.longOrNull != null -> RequestId.NumberId(element.long)
                else -> error("Invalid RequestId type")
            }

            else -> error("Invalid RequestId format")
        }
    }

    override fun serialize(encoder: Encoder, value: RequestId) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only serialize JSON")
        when (value) {
            is RequestId.StringId -> jsonEncoder.encodeString(value.value)
            is RequestId.NumberId -> jsonEncoder.encodeLong(value.value)
        }
    }
}

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta].
 */
@Deprecated(
    message = "Use `CallToolResult.success` instead",
    replaceWith = ReplaceWith("CallToolResult.Companion.success"),
    level = DeprecationLevel.WARNING,
)
public fun CallToolResult.Companion.ok(content: String, meta: JsonObject = EmptyJsonObject): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(content)),
        isError = false,
        _meta = meta,
    )

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta], with `isError` being true.
 */
@Deprecated(
    message = "Use `CallToolResult.error` instead",
    replaceWith = ReplaceWith("CallToolResult.Companion.error"),
    level = DeprecationLevel.WARNING,
)
public fun CallToolResult.Companion.error(content: String, meta: JsonObject = EmptyJsonObject): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(content)),
        isError = true,
        _meta = meta,
    )
