package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/**
 * Base DSL builder for constructing [MediaContent] instances.
 *
 * This abstract class provides common functionality for all media content builders:
 * - [annotations] - Optional metadata annotations
 * - [meta] - Optional additional metadata
 *
 * Concrete implementations:
 * - [TextContentBuilder] - For text content
 * - [ImageContentBuilder] - For image content (base64-encoded)
 * - [AudioContentBuilder] - For audio content (base64-encoded)
 *
 * @see TextContentBuilder
 * @see ImageContentBuilder
 * @see AudioContentBuilder
 */
@McpDsl
public abstract class MediaContentBuilder @PublishedApi internal constructor() {
    protected var annotations: Annotations? = null
    protected var meta: JsonObject? = null

    /**
     * Sets optional annotations for the content.
     *
     * Annotations provide additional metadata about the content such as audience,
     * priority, and last modification time.
     *
     * Example:
     * ```kotlin
     * userText {
     *     text = "Hello"
     *     annotations(Annotations(priority = 0.8))
     * }
     * ```
     *
     * @param annotations The [Annotations] instance
     */
    public fun annotations(annotations: Annotations) {
        this.annotations = annotations
    }

    /**
     * Sets optional annotations for the content with individual parameters.
     *
     * Example with all parameters:
     * ```kotlin
     * userText {
     *     text = "Important update"
     *     annotations(
     *         audience = listOf(Role.User, Role.Assistant),
     *         priority = 0.8,
     *         lastModified = "2025-01-12T15:00:58Z"
     *     )
     * }
     * ```
     *
     * @param audience The intended audience for this content (list of [Role])
     * @param priority Priority hint for this content (0.0 to 1.0)
     * @param lastModified ISO 8601 timestamp of the last modification
     * @see Annotations
     */
    public fun annotations(audience: List<Role>? = null, priority: Double? = null, lastModified: String? = null) {
        this.annotations = Annotations(audience, priority, lastModified)
    }

    /**
     * Sets optional metadata for the content using a DSL builder.
     *
     * Example:
     * ```kotlin
     * userText {
     *     text = "Hello"
     *     meta {
     *         put("source", JsonPrimitive("user-input"))
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the metadata
     */
    public fun meta(block: JsonObjectBuilder.() -> Unit) {
        meta = buildJsonObject(block)
    }

    @PublishedApi
    internal abstract fun build(): MediaContent
}

/**
 * DSL builder for constructing [TextContent] instances.
 *
 * ## Required
 * - [text] - The text content string
 *
 * ## Optional
 * - [annotations] - Content annotations (inherited from [MediaContentBuilder])
 * - [meta] - Additional metadata (inherited from [MediaContentBuilder])
 *
 * Example usage in sampling messages:
 * ```kotlin
 * createMessageRequest {
 *     maxTokens = 100
 *     messages {
 *         userText {
 *             text = "What is the capital of France?"
 *         }
 *     }
 * }
 * ```
 *
 * Example with annotations:
 * ```kotlin
 * userText {
 *     text = "Important message"
 *     annotations(Annotations(priority = 1.0))
 * }
 * ```
 *
 * @see TextContent
 * @see MediaContentBuilder
 */
@McpDsl
public class TextContentBuilder @PublishedApi internal constructor() : MediaContentBuilder() {
    /**
     * The text content. This is a required field.
     */
    public var text: String? = null

    override fun build(): TextContent {
        val text = requireNotNull(text) {
            "Missing required field 'text'. Example: text = \"Hello, world!\""
        }

        return TextContent(text = text, annotations = annotations, meta = meta)
    }
}

/**
 * DSL builder for constructing [ImageContent] instances.
 *
 * ## Required
 * - [data] - The base64-encoded image data
 * - [mimeType] - The MIME type of the image (e.g., "image/png", "image/jpeg")
 *
 * ## Optional
 * - [annotations] - Content annotations (inherited from [MediaContentBuilder])
 * - [meta] - Additional metadata (inherited from [MediaContentBuilder])
 *
 * Example usage in sampling messages:
 * ```kotlin
 * createMessageRequest {
 *     maxTokens = 100
 *     messages {
 *         userImage {
 *             data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB..."
 *             mimeType = "image/png"
 *         }
 *     }
 * }
 * ```
 *
 * Example with annotations:
 * ```kotlin
 * userImage {
 *     data = base64EncodedImageData
 *     mimeType = "image/jpeg"
 *     annotations(Annotations(priority = 0.9))
 * }
 * ```
 *
 * @see ImageContent
 * @see MediaContentBuilder
 */
@McpDsl
public class ImageContentBuilder @PublishedApi internal constructor() : MediaContentBuilder() {
    /**
     * The base64-encoded image data. This is a required field.
     */
    public var data: String? = null

    /**
     * The MIME type of the image (e.g., "image/png", "image/jpeg"). This is a required field.
     */
    public var mimeType: String? = null

    override fun build(): ImageContent {
        val data = requireNotNull(data) {
            "Missing required field 'data'. Provide base64-encoded image data"
        }
        val mime = requireNotNull(mimeType) {
            "Missing required field 'mimeType'. Example: mimeType = \"image/png\""
        }

        return ImageContent(
            data = data,
            mimeType = mime,
            annotations = annotations,
            meta = meta,
        )
    }
}

/**
 * DSL builder for constructing [AudioContent] instances.
 *
 * ## Required
 * - [data] - The base64-encoded audio data
 * - [mimeType] - The MIME type of the audio (e.g., "audio/wav", "audio/mpeg")
 *
 * ## Optional
 * - [annotations] - Content annotations (inherited from [MediaContentBuilder])
 * - [meta] - Additional metadata (inherited from [MediaContentBuilder])
 *
 * Example usage in sampling messages:
 * ```kotlin
 * createMessageRequest {
 *     maxTokens = 100
 *     messages {
 *         userAudio {
 *             data = "UklGRiQAAABXQVZFZm10IBAAAAABAAEA..."
 *             mimeType = "audio/wav"
 *         }
 *     }
 * }
 * ```
 *
 * Example with annotations:
 * ```kotlin
 * userAudio {
 *     data = base64EncodedAudioData
 *     mimeType = "audio/mpeg"
 *     annotations(Annotations(priority = 0.7))
 * }
 * ```
 *
 * @see AudioContent
 * @see MediaContentBuilder
 */
@McpDsl
public class AudioContentBuilder @PublishedApi internal constructor() : MediaContentBuilder() {
    /**
     * The base64-encoded audio data. This is a required field.
     */
    public var data: String? = null

    /**
     * The MIME type of the audio (e.g., "audio/wav", "audio/mpeg"). This is a required field.
     */
    public var mimeType: String? = null

    override fun build(): AudioContent {
        val data = requireNotNull(data) {
            "Missing required field 'data'. Provide base64-encoded audio data"
        }
        val mime = requireNotNull(mimeType) {
            "Missing required field 'mimeType'. Example: mimeType = \"audio/wav\""
        }

        return AudioContent(
            data = data,
            mimeType = mime,
            annotations = annotations,
            meta = meta,
        )
    }
}
