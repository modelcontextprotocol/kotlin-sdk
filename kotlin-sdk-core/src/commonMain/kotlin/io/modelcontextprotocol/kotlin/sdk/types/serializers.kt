package io.modelcontextprotocol.kotlin.sdk.types

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val logger = KotlinLogging.logger {}

internal object MethodSerializer : KSerializer<Method> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.modelcontextprotocol.kotlin.sdk.types.Method", PrimitiveKind.STRING)

    private val definedMethods: Map<String, Method.Defined> by lazy {
        Method.Defined.entries.associateBy { it.value }
    }

    override fun serialize(encoder: Encoder, value: Method) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Method {
        val decodedString = decoder.decodeString()
        return definedMethods[decodedString] ?: Method.Custom(decodedString)
    }
}

internal object ReferencePolymorphicSerializer : JsonContentPolymorphicSerializer<Reference>(Reference::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Reference> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ReferenceType.Prompt.value -> PromptReference.serializer()
            ReferenceType.ResourceTemplate.value -> ResourceTemplateReference.serializer()
            else -> error("Unknown reference type")
        }
}

internal object ContentBlockPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ContentBlock>(ContentBlock::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ContentBlock> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ContentTypes.TEXT.value -> TextContent.serializer()
            ContentTypes.IMAGE.value -> ImageContent.serializer()
            ContentTypes.AUDIO.value -> AudioContent.serializer()
            ContentTypes.RESOURCE_LINK.value -> ResourceLink.serializer()
            ContentTypes.EMBEDDED_RESOURCE.value -> EmbeddedResource.serializer()
            else -> error("Unknown content block type")
        }
}

internal object MediaContentPolymorphicSerializer :
    JsonContentPolymorphicSerializer<MediaContent>(MediaContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MediaContent> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ContentTypes.TEXT.value -> TextContent.serializer()
            ContentTypes.IMAGE.value -> ImageContent.serializer()
            ContentTypes.AUDIO.value -> AudioContent.serializer()
            else -> error("Unknown media content type")
        }
}

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

internal fun selectClientRequestDeserializer(method: String): DeserializationStrategy<ClientRequest>? = when (method) {
    Method.Defined.CompletionComplete.value -> CompleteRequest.serializer()
    Method.Defined.Initialize.value -> InitializeRequest.serializer()
    Method.Defined.Ping.value -> PingRequest.serializer()
    Method.Defined.LoggingSetLevel.value -> SetLevelRequest.serializer()
    Method.Defined.PromptsGet.value -> GetPromptRequest.serializer()
    Method.Defined.PromptsList.value -> ListPromptsRequest.serializer()
    Method.Defined.ResourcesList.value -> ListResourcesRequest.serializer()
    Method.Defined.ResourcesRead.value -> ReadResourceRequest.serializer()
    Method.Defined.ResourcesSubscribe.value -> SubscribeRequest.serializer()
    Method.Defined.ResourcesUnsubscribe.value -> UnsubscribeRequest.serializer()
    Method.Defined.ResourcesTemplatesList.value -> ListResourceTemplatesRequest.serializer()
    Method.Defined.ToolsCall.value -> CallToolRequest.serializer()
    Method.Defined.ToolsList.value -> ListToolsRequest.serializer()
    else -> null
}

private fun JsonElement.getMethodOrNull(): String? = jsonObject["method"]?.jsonPrimitive?.content
private fun JsonElement.getMethod(): String =
    getMethodOrNull() ?: throw SerializationException("Missing required 'method' field in notification")

private fun selectClientNotificationDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification>? {
    val method = element.getMethodOrNull() ?: return null

    return when (method) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsInitialized.value -> InitializedNotification.serializer()
        Method.Defined.NotificationsRootsListChanged.value -> RootsListChangedNotification.serializer()
        else -> null
    }
}

internal object ClientNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientNotification>(ClientNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification> =
        selectClientNotificationDeserializer(element)
            ?: CustomNotification.serializer()
}

internal fun selectServerRequestDeserializer(method: String): DeserializationStrategy<ServerRequest>? = when (method) {
    Method.Defined.ElicitationCreate.value -> ElicitRequest.serializer()
    Method.Defined.Ping.value -> PingRequest.serializer()
    Method.Defined.RootsList.value -> ListRootsRequest.serializer()
    Method.Defined.SamplingCreateMessage.value -> CreateMessageRequest.serializer()
    else -> null
}

internal fun selectServerNotificationDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification>? {
    val method = element.getMethodOrNull() ?: return null

    return when (method) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsMessage.value -> LoggingMessageNotification.serializer()
        Method.Defined.NotificationsResourcesUpdated.value -> ResourceUpdatedNotification.serializer()
        Method.Defined.NotificationsResourcesListChanged.value -> ResourceListChangedNotification.serializer()
        Method.Defined.NotificationsToolsListChanged.value -> ToolListChangedNotification.serializer()
        Method.Defined.NotificationsPromptsListChanged.value -> PromptListChangedNotification.serializer()
        else -> null
    }
}

internal object ServerNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerNotification>(ServerNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification> =
        selectServerNotificationDeserializer(element)
            ?: CustomNotification.serializer()
}

internal object NotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Notification>(Notification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Notification> =
        selectClientNotificationDeserializer(element)
            ?: selectServerNotificationDeserializer(element)
            ?: CustomNotification.serializer()
}

internal object RequestPolymorphicSerializer : JsonContentPolymorphicSerializer<Request>(Request::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Request> {
        val method = element.getMethodOrNull() ?: run {
            logger.error { "No method in $element" }
            error("No method in $element")
        }

        return selectClientRequestDeserializer(method)
            ?: selectServerRequestDeserializer(method)
            ?: CustomRequest.serializer()
    }
}

private fun selectServerResultDeserializer(element: JsonElement): DeserializationStrategy<ServerResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("protocolVersion") && jsonObject.contains("capabilities") -> InitializeResult.serializer()
        jsonObject.contains("completion") -> CompleteResult.serializer()
        jsonObject.contains("tools") -> ListToolsResult.serializer()
        jsonObject.contains("resources") -> ListResourcesResult.serializer()
        jsonObject.contains("resourceTemplates") -> ListResourceTemplatesResult.serializer()
        jsonObject.contains("prompts") -> ListPromptsResult.serializer()
        jsonObject.contains("messages") -> GetPromptResult.serializer()
        jsonObject.contains("contents") -> ReadResourceResult.serializer()
        jsonObject.contains("content") -> CallToolResult.serializer()
        else -> null
    }
}

private fun selectClientResultDeserializer(element: JsonElement): DeserializationStrategy<ClientResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("model") && jsonObject.contains("role") -> CreateMessageResult.serializer()
        jsonObject.contains("roots") -> ListRootsResult.serializer()
        jsonObject.contains("action") -> ElicitResult.serializer()
        else -> null
    }
}

private fun selectEmptyResult(element: JsonElement): DeserializationStrategy<EmptyResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.isEmpty() || (jsonObject.size == 1 && jsonObject.contains("_meta")) -> EmptyResult.serializer()
        else -> null
    }
}

internal object ServerResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerResult>(ServerResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerResult> =
        selectServerResultDeserializer(element)
            ?: selectEmptyResult(element)
            ?: throw SerializationException("Cannot determine RequestResult type from JSON: ${element.jsonObject.keys}")
}

internal object ClientResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientResult>(ClientResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientResult> =
        selectClientResultDeserializer(element)
            ?: selectEmptyResult(element)
            ?: throw SerializationException("Cannot determine RequestResult type from JSON: ${element.jsonObject.keys}")
}

internal object RequestResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<RequestResult>(RequestResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestResult> =
        selectClientResultDeserializer(element)
            ?: selectServerResultDeserializer(element)
            ?: selectEmptyResult(element)
            ?: throw SerializationException("Cannot determine RequestResult type from JSON: ${element.jsonObject.keys}")
}

internal object JSONRPCMessagePolymorphicSerializer :
    JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("error") -> JSONRPCError.serializer()
            jsonObject.contains("result") -> JSONRPCResponse.serializer()
            jsonObject.contains("method") && jsonObject.contains("id") -> JSONRPCRequest.serializer()
            jsonObject.contains("method") -> JSONRPCNotification.serializer()
            else -> error("Invalid JSONRPCMessage type")
        }
    }
}

internal object RequestIdPolymorphicSerializer : JsonContentPolymorphicSerializer<RequestId>(RequestId::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestId> = when (element) {
        is JsonPrimitive if (element.isString) -> RequestId.StringId.serializer()
        is JsonPrimitive if (element.longOrNull != null) -> RequestId.NumberId.serializer()
        else -> error("Invalid RequestId type")
    }
}
