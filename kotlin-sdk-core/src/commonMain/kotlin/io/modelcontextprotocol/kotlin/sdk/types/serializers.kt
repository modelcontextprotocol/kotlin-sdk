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

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Safely extracts the method field from a JSON element.
 * Returns null if the method field is not present.
 */
private fun JsonElement.getMethodOrNull(): String? = jsonObject["method"]?.jsonPrimitive?.content

/**
 * Extracts the method field from a JSON element.
 * Throws [SerializationException] if the method field is not present.
 */
private fun JsonElement.getMethod(): String =
    getMethodOrNull() ?: throw SerializationException("Missing required 'method' field in notification")

// ============================================================================
// Method Serializer
// ============================================================================

/**
 * Custom serializer for [Method] that handles both defined and custom methods.
 * Defined methods are deserialized to [Method.Defined], while unknown methods
 * are deserialized to [Method.Custom].
 */
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

// ============================================================================
// Reference Serializers
// ============================================================================

/**
 * Polymorphic serializer for [Reference] types.
 * Determines the concrete type based on the "type" field in JSON.
 */
internal object ReferencePolymorphicSerializer : JsonContentPolymorphicSerializer<Reference>(Reference::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Reference> =
        when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ReferenceType.Prompt.value -> PromptReference.serializer()
            ReferenceType.ResourceTemplate.value -> ResourceTemplateReference.serializer()
            else -> error("Unknown reference type")
        }
}

// ============================================================================
// Content Serializers
// ============================================================================

/**
 * Polymorphic serializer for [ContentBlock] types.
 * Determines the concrete type based on the "type" field in JSON.
 */
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

/**
 * Polymorphic serializer for [MediaContent] types (text, image, audio).
 * Determines the concrete type based on the "type" field in JSON.
 */
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

// ============================================================================
// Resource Serializers
// ============================================================================

/**
 * Polymorphic serializer for [ResourceContents] types.
 * Determines the concrete type based on the presence of "text" or "blob" fields.
 */
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

// ============================================================================
// Request Serializers
// ============================================================================

/**
 * Selects the appropriate deserializer for client requests based on the method name.
 * Returns null if the method is not a known client request method.
 */
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

/**
 * Selects the appropriate deserializer for server requests based on the method name.
 * Returns null if the method is not a known server request method.
 */
internal fun selectServerRequestDeserializer(method: String): DeserializationStrategy<ServerRequest>? = when (method) {
    Method.Defined.ElicitationCreate.value -> ElicitRequest.serializer()
    Method.Defined.Ping.value -> PingRequest.serializer()
    Method.Defined.RootsList.value -> ListRootsRequest.serializer()
    Method.Defined.SamplingCreateMessage.value -> CreateMessageRequest.serializer()
    else -> null
}

/**
 * Polymorphic serializer for [Request] types.
 * Supports both client and server requests, as well as custom requests.
 */
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

// ============================================================================
// Notification Serializers
// ============================================================================

/**
 * Selects the appropriate deserializer for client notifications based on the method name.
 * Returns null if the method is not a known client notification method.
 */
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

/**
 * Selects the appropriate deserializer for server notifications based on the method name.
 * Returns null if the method is not a known server notification method.
 */
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

/**
 * Polymorphic serializer for [Notification] types.
 * Supports both client and server notifications, as well as custom notifications.
 */
internal object NotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Notification>(Notification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Notification> =
        selectClientNotificationDeserializer(element)
            ?: selectServerNotificationDeserializer(element)
            ?: CustomNotification.serializer()
}

/**
 * Polymorphic serializer for [ClientNotification] types.
 * Falls back to [CustomNotification] for unknown methods.
 */
internal object ClientNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientNotification>(ClientNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification> =
        selectClientNotificationDeserializer(element)
            ?: CustomNotification.serializer()
}

/**
 * Polymorphic serializer for [ServerNotification] types.
 * Falls back to [CustomNotification] for unknown methods.
 */
internal object ServerNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerNotification>(ServerNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification> =
        selectServerNotificationDeserializer(element)
            ?: CustomNotification.serializer()
}

// ============================================================================
// Result Serializers
// ============================================================================

/**
 * Selects the appropriate deserializer for empty results.
 * Returns EmptyResult serializer if the JSON object is empty or contains only metadata.
 */
private fun selectEmptyResult(element: JsonElement): DeserializationStrategy<EmptyResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.isEmpty() || (jsonObject.size == 1 && jsonObject.contains("_meta")) -> EmptyResult.serializer()
        else -> null
    }
}

/**
 * Selects the appropriate deserializer for client results based on JSON content.
 * Returns null if the structure doesn't match any known client result type.
 */
private fun selectClientResultDeserializer(element: JsonElement): DeserializationStrategy<ClientResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("model") && jsonObject.contains("role") -> CreateMessageResult.serializer()
        jsonObject.contains("roots") -> ListRootsResult.serializer()
        jsonObject.contains("action") -> ElicitResult.serializer()
        else -> null
    }
}

/**
 * Selects the appropriate deserializer for server results based on JSON content.
 * Returns null if the structure doesn't match any known server result type.
 */
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

/**
 * Polymorphic serializer for [RequestResult] types.
 * Supports both client and server results.
 * Throws [SerializationException] if the result type cannot be determined.
 */
internal object RequestResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<RequestResult>(RequestResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestResult> =
        selectClientResultDeserializer(element)
            ?: selectServerResultDeserializer(element)
            ?: selectEmptyResult(element)
            ?: throw SerializationException("Cannot determine RequestResult type from JSON: ${element.jsonObject.keys}")
}

/**
 * Polymorphic serializer for [ClientResult] types.
 * Throws [SerializationException] if the result type cannot be determined.
 */
internal object ClientResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientResult>(ClientResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientResult> =
        selectClientResultDeserializer(element)
            ?: selectEmptyResult(element)
            ?: throw SerializationException("Cannot determine RequestResult type from JSON: ${element.jsonObject.keys}")
}

/**
 * Polymorphic serializer for [ServerResult] types.
 * Throws [SerializationException] if the result type cannot be determined.
 */
internal object ServerResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerResult>(ServerResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerResult> =
        selectServerResultDeserializer(element)
            ?: selectEmptyResult(element)
            ?: throw SerializationException("Cannot determine RequestResult type from JSON: ${element.jsonObject.keys}")
}

// ============================================================================
// JSON-RPC Serializers
// ============================================================================

/**
 * Polymorphic serializer for [JSONRPCMessage] types.
 * Determines the message type based on the presence of specific fields:
 * - "error" -> JSONRPCError
 * - "result" -> JSONRPCResponse
 * - "method" + "id" -> JSONRPCRequest
 * - "method" -> JSONRPCNotification
 */
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

/**
 * Polymorphic serializer for [RequestId] types.
 * Supports both string and number IDs.
 */
internal object RequestIdPolymorphicSerializer : JsonContentPolymorphicSerializer<RequestId>(RequestId::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestId> = when (element) {
        is JsonPrimitive if (element.isString) -> RequestId.StringId.serializer()
        is JsonPrimitive if (element.longOrNull != null) -> RequestId.NumberId.serializer()
        else -> error("Invalid RequestId type")
    }
}
