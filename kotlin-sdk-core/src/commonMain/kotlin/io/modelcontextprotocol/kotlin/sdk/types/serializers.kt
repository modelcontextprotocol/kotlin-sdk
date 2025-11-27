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
import kotlinx.serialization.json.JsonObject
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
 * Safely extracts the type field from a JSON element.
 * Returns null if the type field is not present.
 */
private fun JsonElement.getTypeOrNull(): String? = jsonObject["type"]?.jsonPrimitive?.content

/**
 * Extracts the type field from a JSON element.
 * Throws [SerializationException] if the type field is not present.
 */
private fun JsonElement.getType(): String = requireNotNull(getTypeOrNull()) { "Missing required 'type' field" }

@Throws(SerializationException::class)
private fun JsonElement.asJsonObject(): JsonObject {
    if (this !is JsonObject) {
        throw SerializationException("Invalid response. JsonObject expected, got: ${this::class.simpleName}")
    }
    val jsonObject = this.jsonObject
    return jsonObject
}

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
        when (element.getType()) {
            ReferenceType.Prompt.value -> PromptReference.serializer()
            ReferenceType.ResourceTemplate.value -> ResourceTemplateReference.serializer()
            else -> throw SerializationException("Unknown reference type: ${element.getTypeOrNull()}")
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
        when (element.getType()) {
            ContentTypes.TEXT.value -> TextContent.serializer()
            ContentTypes.IMAGE.value -> ImageContent.serializer()
            ContentTypes.AUDIO.value -> AudioContent.serializer()
            ContentTypes.RESOURCE_LINK.value -> ResourceLink.serializer()
            ContentTypes.EMBEDDED_RESOURCE.value -> EmbeddedResource.serializer()
            else -> throw SerializationException("Unknown content block type: ${element.getTypeOrNull()}")
        }
}

/**
 * Polymorphic serializer for [MediaContent] types (text, image, audio).
 * Determines the concrete type based on the "type" field in JSON.
 */
internal object MediaContentPolymorphicSerializer :
    JsonContentPolymorphicSerializer<MediaContent>(MediaContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MediaContent> =
        when (element.getType()) {
            ContentTypes.TEXT.value -> TextContent.serializer()
            ContentTypes.IMAGE.value -> ImageContent.serializer()
            ContentTypes.AUDIO.value -> AudioContent.serializer()
            else -> throw SerializationException("Unknown media content type: ${element.getTypeOrNull()}")
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
        val jsonObject = element.asJsonObject()
        return when {
            "text" in jsonObject -> TextResourceContents.serializer()
            "blob" in jsonObject -> BlobResourceContents.serializer()
            else -> UnknownResourceContents.serializer()
        }
    }
}

// ============================================================================
// Request Serializers
// ============================================================================

private val clientRequestDeserializers: Map<String, DeserializationStrategy<ClientRequest>> by lazy {
    mapOf(
        Method.Defined.CompletionComplete.value to CompleteRequest.serializer(),
        Method.Defined.Initialize.value to InitializeRequest.serializer(),
        Method.Defined.Ping.value to PingRequest.serializer(),
        Method.Defined.LoggingSetLevel.value to SetLevelRequest.serializer(),
        Method.Defined.PromptsGet.value to GetPromptRequest.serializer(),
        Method.Defined.PromptsList.value to ListPromptsRequest.serializer(),
        Method.Defined.ResourcesList.value to ListResourcesRequest.serializer(),
        Method.Defined.ResourcesRead.value to ReadResourceRequest.serializer(),
        Method.Defined.ResourcesSubscribe.value to SubscribeRequest.serializer(),
        Method.Defined.ResourcesUnsubscribe.value to UnsubscribeRequest.serializer(),
        Method.Defined.ResourcesTemplatesList.value to ListResourceTemplatesRequest.serializer(),
        Method.Defined.ToolsCall.value to CallToolRequest.serializer(),
        Method.Defined.ToolsList.value to ListToolsRequest.serializer(),
    )
}

/**
 * Selects the appropriate deserializer for client requests based on the method name.
 * Returns null if the method is not a known client request method.
 */
internal fun selectClientRequestDeserializer(method: String): DeserializationStrategy<ClientRequest>? =
    clientRequestDeserializers[method]

private val serverRequestDeserializers: Map<String, DeserializationStrategy<ServerRequest>> by lazy {
    mapOf(
        Method.Defined.ElicitationCreate.value to ElicitRequest.serializer(),
        Method.Defined.Ping.value to PingRequest.serializer(),
        Method.Defined.RootsList.value to ListRootsRequest.serializer(),
        Method.Defined.SamplingCreateMessage.value to CreateMessageRequest.serializer(),
    )
}

/**
 * Selects the appropriate deserializer for server requests based on the method name.
 * Returns null if the method is not a known server request method.
 */
internal fun selectServerRequestDeserializer(method: String): DeserializationStrategy<ServerRequest>? =
    serverRequestDeserializers[method]

/**
 * Polymorphic serializer for [Request] types.
 * Supports both client and server requests, as well as custom requests.
 */
internal object RequestPolymorphicSerializer : JsonContentPolymorphicSerializer<Request>(Request::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Request> {
        val method = element.getMethodOrNull() ?: run {
            logger.error { "Missing 'method' for Request: $element" }
            throw SerializationException("Missing 'method' for Request: $element")
        }

        return selectClientRequestDeserializer(method)
            ?: selectServerRequestDeserializer(method)
            ?: CustomRequest.serializer()
    }
}

// ============================================================================
// Notification Serializers
// ============================================================================

private val clientNotificationDeserializers: Map<String, DeserializationStrategy<ClientNotification>> by lazy {
    mapOf(
        Method.Defined.NotificationsCancelled.value to CancelledNotification.serializer(),
        Method.Defined.NotificationsProgress.value to ProgressNotification.serializer(),
        Method.Defined.NotificationsInitialized.value to InitializedNotification.serializer(),
        Method.Defined.NotificationsRootsListChanged.value to RootsListChangedNotification.serializer(),
    )
}

/**
 * Selects the appropriate deserializer for client notifications based on the method name.
 * Returns null if the method is not a known client notification method.
 */
private fun selectClientNotificationDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification>? =
    element.getMethodOrNull()?.let(clientNotificationDeserializers::get)

private val serverNotificationDeserializers: Map<String, DeserializationStrategy<ServerNotification>> by lazy {
    mapOf(
        Method.Defined.NotificationsCancelled.value to CancelledNotification.serializer(),
        Method.Defined.NotificationsProgress.value to ProgressNotification.serializer(),
        Method.Defined.NotificationsMessage.value to LoggingMessageNotification.serializer(),
        Method.Defined.NotificationsResourcesUpdated.value to ResourceUpdatedNotification.serializer(),
        Method.Defined.NotificationsResourcesListChanged.value to ResourceListChangedNotification.serializer(),
        Method.Defined.NotificationsToolsListChanged.value to ToolListChangedNotification.serializer(),
        Method.Defined.NotificationsPromptsListChanged.value to PromptListChangedNotification.serializer(),
    )
}

/**
 * Selects the appropriate deserializer for server notifications based on the method name.
 * Returns null if the method is not a known server notification method.
 */
internal fun selectServerNotificationDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification>? =
    element.getMethodOrNull()?.let(serverNotificationDeserializers::get)

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
    val jsonObject = element.asJsonObject()
    return when {
        jsonObject.isEmpty() || (jsonObject.size == 1 && "_meta" in jsonObject) -> EmptyResult.serializer()
        else -> null
    }
}

/**
 * Selects the appropriate deserializer for client results based on JSON content.
 * Returns null if the structure doesn't match any known client result type.
 */
private fun selectClientResultDeserializer(element: JsonElement): DeserializationStrategy<ClientResult>? {
    val jsonObject = element.asJsonObject()
    return when {
        "model" in jsonObject && "role" in jsonObject -> CreateMessageResult.serializer()
        "roots" in jsonObject -> ListRootsResult.serializer()
        "action" in jsonObject -> ElicitResult.serializer()
        else -> null
    }
}

/**
 * Selects the appropriate deserializer for server results based on JSON content.
 * Returns null if the structure doesn't match any known server result type.
 */
private fun selectServerResultDeserializer(element: JsonElement): DeserializationStrategy<ServerResult>? {
    val jsonObject = element.asJsonObject()
    return when {
        "protocolVersion" in jsonObject && "capabilities" in jsonObject -> InitializeResult.serializer()
        "completion" in jsonObject -> CompleteResult.serializer()
        "tools" in jsonObject -> ListToolsResult.serializer()
        "resources" in jsonObject -> ListResourcesResult.serializer()
        "resourceTemplates" in jsonObject -> ListResourceTemplatesResult.serializer()
        "prompts" in jsonObject -> ListPromptsResult.serializer()
        "messages" in jsonObject -> GetPromptResult.serializer()
        "contents" in jsonObject -> ReadResourceResult.serializer()
        "content" in jsonObject -> CallToolResult.serializer()
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
        val jsonObject = element.asJsonObject()
        return when {
            "error" in jsonObject -> JSONRPCError.serializer()
            "result" in jsonObject -> JSONRPCResponse.serializer()
            "method" in jsonObject && "id" in jsonObject -> JSONRPCRequest.serializer()
            "method" in jsonObject -> JSONRPCNotification.serializer()
            else -> throw SerializationException("Invalid JSONRPCMessage type: ${jsonObject.keys}")
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
        else -> throw SerializationException("Invalid RequestId type: $element")
    }
}
