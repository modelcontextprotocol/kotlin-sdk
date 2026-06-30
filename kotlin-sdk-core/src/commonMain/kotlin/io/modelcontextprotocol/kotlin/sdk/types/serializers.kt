package io.modelcontextprotocol.kotlin.sdk.types

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

/**
 * Polymorphic serializer for [SamplingMessageContent] types.
 * Determines the concrete type based on the "type" field in JSON.
 */
internal object SamplingMessageContentPolymorphicSerializer :
    JsonContentPolymorphicSerializer<SamplingMessageContent>(SamplingMessageContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SamplingMessageContent> =
        when (element.getType()) {
            ContentTypes.TEXT.value -> TextContent.serializer()

            ContentTypes.IMAGE.value -> ImageContent.serializer()

            ContentTypes.AUDIO.value -> AudioContent.serializer()

            ContentTypes.TOOL_USE.value -> ToolUseContent.serializer()

            ContentTypes.TOOL_RESULT.value -> ToolResultContent.serializer()

            else -> throw SerializationException(
                "Unknown sampling message content type: ${element.getTypeOrNull()}",
            )
        }
}

/**
 * Wire-format serializer for `List<SamplingMessageContent>` honouring SEP-1577's
 * single-object-or-array content shape.
 *
 * **Why this exists.** SEP-1577 widens the `content` field of [SamplingMessage] and
 * [CreateMessageResult] from a single content block to either a single block or an
 * array of blocks. Pre-SEP peers only understand a single object on the wire; post-SEP
 * peers may send arrays when a turn carries multiple blocks (e.g. an assistant reply
 * containing `[TextContent, ToolUseContent]` during a tool-loop step).
 *
 * The Kotlin API uses `List<SamplingMessageContent>` uniformly to avoid branching in
 * caller code. This serializer bridges the type difference:
 *
 * - **Read:** accepts both shapes; a single JSON object becomes `listOf(block)`, an
 *   array becomes the decoded list.
 * - **Write:** size-1 list → single object (wire-compatible with pre-SEP peers);
 *   size≥2 → array; empty list throws (sampling content has no valid empty meaning
 *   per spec).
 */
internal object SamplingContentSerializer : KSerializer<List<SamplingMessageContent>> {

    private val listSerializer = ListSerializer(SamplingMessageContentPolymorphicSerializer)

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: List<SamplingMessageContent>) {
        check(value.isNotEmpty()) { "content must contain at least one block" }
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("SamplingContentSerializer requires a Json encoder")
        if (value.size == 1) {
            jsonEncoder.encodeJsonElement(
                jsonEncoder.json.encodeToJsonElement(
                    SamplingMessageContentPolymorphicSerializer,
                    value[0],
                ),
            )
        } else {
            jsonEncoder.encodeJsonElement(
                jsonEncoder.json.encodeToJsonElement(listSerializer, value),
            )
        }
    }

    override fun deserialize(decoder: Decoder): List<SamplingMessageContent> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("SamplingContentSerializer requires a Json decoder")
        return decodeFromElement(jsonDecoder, jsonDecoder.decodeJsonElement())
    }

    private fun decodeFromElement(jsonDecoder: JsonDecoder, element: JsonElement): List<SamplingMessageContent> =
        when (element) {
            is JsonArray -> {
                if (element.isEmpty()) {
                    throw SerializationException("content must contain at least one block")
                }
                jsonDecoder.json.decodeFromJsonElement(listSerializer, element)
            }

            is JsonObject -> listOf(
                jsonDecoder.json.decodeFromJsonElement(
                    SamplingMessageContentPolymorphicSerializer,
                    element,
                ),
            )

            else -> throw SerializationException(
                "content must be a JSON object or array of objects, got $element",
            )
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
        Method.Defined.TasksGet.value to GetTaskRequest.serializer(),
        Method.Defined.TasksResult.value to GetTaskPayloadRequest.serializer(),
        Method.Defined.TasksList.value to ListTasksRequest.serializer(),
        Method.Defined.TasksCancel.value to CancelTaskRequest.serializer(),
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
        Method.Defined.TasksGet.value to GetTaskRequest.serializer(),
        Method.Defined.TasksResult.value to GetTaskPayloadRequest.serializer(),
        Method.Defined.TasksList.value to ListTasksRequest.serializer(),
        Method.Defined.TasksCancel.value to CancelTaskRequest.serializer(),
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
        Method.Defined.NotificationsTasksStatus.value to TaskStatusNotification.serializer(),
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
        Method.Defined.NotificationsElicitationComplete.value to ElicitationCompleteNotification.serializer(),
        Method.Defined.NotificationsTasksStatus.value to TaskStatusNotification.serializer(),
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
    val jsonObject = element.jsonObject
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
    val jsonObject = element.jsonObject
    return when {
        "model" in jsonObject && "role" in jsonObject -> CreateMessageResult.serializer()
        "roots" in jsonObject -> ListRootsResult.serializer()
        "action" in jsonObject -> ElicitResult.serializer()
        "task" in jsonObject -> CreateTaskResult.serializer()
        "tasks" in jsonObject -> ListTasksResult.serializer()
        "taskId" in jsonObject -> GetTaskResult.serializer()
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
        "protocolVersion" in jsonObject && "capabilities" in jsonObject -> InitializeResult.serializer()
        "completion" in jsonObject -> CompleteResult.serializer()
        "tools" in jsonObject -> ListToolsResult.serializer()
        "resources" in jsonObject -> ListResourcesResult.serializer()
        "resourceTemplates" in jsonObject -> ListResourceTemplatesResult.serializer()
        "prompts" in jsonObject -> ListPromptsResult.serializer()
        "messages" in jsonObject -> GetPromptResult.serializer()
        "contents" in jsonObject -> ReadResourceResult.serializer()
        "content" in jsonObject -> CallToolResult.serializer()
        "task" in jsonObject -> CreateTaskResult.serializer()
        "tasks" in jsonObject -> ListTasksResult.serializer()
        "taskId" in jsonObject -> GetTaskResult.serializer()
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
 * - "result" + "id" -> JSONRPCResponse
 * - "result" -> JSONRPCEmptyMessage
 * - "method" + "id" -> JSONRPCRequest
 * - "method" -> JSONRPCNotification
 */
internal object JSONRPCMessagePolymorphicSerializer :
    JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCMessage> {
        val jsonObj = element.jsonObject
        return when {
            "error" in jsonObj -> JSONRPCError.serializer()
            "result" in jsonObj && "id" in jsonObj -> JSONRPCResponse.serializer()
            "result" in jsonObj && jsonObj["result"]?.jsonObject?.isEmpty() == true -> JSONRPCEmptyMessage.serializer()
            "method" in jsonObj && "id" in jsonObj -> JSONRPCRequest.serializer()
            "method" in jsonObj -> JSONRPCNotification.serializer()
            jsonObj.isEmpty() || jsonObj.keys == setOf("jsonrpc") -> JSONRPCEmptyMessage.serializer()
            else -> throw SerializationException("Invalid JSONRPCMessage type: ${jsonObj.keys}")
        }
    }
}

/**
 * Polymorphic serializer for [RequestId] types.
 * Supports both string and number IDs.
 */
internal object RequestIdPolymorphicSerializer : JsonContentPolymorphicSerializer<RequestId>(RequestId::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestId> = when {
        element is JsonPrimitive && element.isString -> RequestId.StringId.serializer()
        element is JsonPrimitive && element.longOrNull != null -> RequestId.NumberId.serializer()
        else -> throw SerializationException("Invalid RequestId type: $element")
    }
}

// ============================================================================
// ElicitationParams Serializers
// ============================================================================

internal object ElicitRequestParamsSerializer : JsonContentPolymorphicSerializer<ElicitRequestParams>(
    ElicitRequestParams::class,
) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ElicitRequestParams> {
        val mode = when (val modeElement = element.jsonObject["mode"]) {
            null -> null

            is JsonPrimitive -> {
                if (!modeElement.isString) {
                    throw SerializationException("Invalid 'mode' type: expected string but was: $modeElement")
                }
                modeElement.contentOrNull
            }

            else -> throw SerializationException("Invalid 'mode' type: expected string but was: $modeElement")
        }
        return when (mode) {
            "url" -> ElicitRequestURLParams.serializer()
            "form", null -> ElicitRequestFormParams.serializer()
            else -> throw SerializationException("Unsupported elicitation mode: '$mode'")
        }
    }
}

// ============================================================================
// PrimitiveSchemaDefinition Serializers
// ============================================================================

internal object PrimitiveSchemaDefinitionSerializer : JsonContentPolymorphicSerializer<PrimitiveSchemaDefinition>(
    PrimitiveSchemaDefinition::class,
) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PrimitiveSchemaDefinition> {
        val obj = element.jsonObject
        val typeElement = obj["type"]
            ?: throw SerializationException("Missing 'type' in PrimitiveSchemaDefinition: $element")
        val type = if (typeElement is JsonPrimitive && typeElement.isString) {
            typeElement.content
        } else {
            throw SerializationException("Expected 'type' to be a string but was: $typeElement")
        }
        return when (type) {
            "boolean" -> BooleanSchema.serializer()
            "integer" -> IntegerSchema.serializer()
            "number" -> DoubleSchema.serializer()
            "string" -> selectStringTypeDeserializer(obj)
            "array" -> selectArrayTypeDeserializer(obj)
            else -> throw SerializationException("Unknown PrimitiveSchemaDefinition type: '$type'")
        }
    }

    private fun selectArrayTypeDeserializer(
        obj: Map<String, JsonElement>,
    ): DeserializationStrategy<PrimitiveSchemaDefinition> {
        val items = obj["items"]?.jsonObject
        return when {
            items != null && "anyOf" in items -> TitledMultiSelectEnumSchema.serializer()
            else -> UntitledMultiSelectEnumSchema.serializer()
        }
    }

    private fun selectStringTypeDeserializer(
        obj: Map<String, JsonElement>,
    ): DeserializationStrategy<PrimitiveSchemaDefinition> = when {
        "enumNames" in obj -> LegacyTitledEnumSchema.serializer()
        "enum" in obj -> UntitledSingleSelectEnumSchema.serializer()
        "oneOf" in obj -> TitledSingleSelectEnumSchema.serializer()
        else -> StringSchema.serializer()
    }
}
