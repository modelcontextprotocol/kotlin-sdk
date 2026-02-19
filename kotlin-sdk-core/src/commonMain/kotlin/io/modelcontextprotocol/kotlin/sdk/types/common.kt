package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Protocol Version Constants
// ============================================================================

public const val LATEST_PROTOCOL_VERSION: String = "2025-11-25"

public const val DEFAULT_NEGOTIATED_PROTOCOL_VERSION: String = "2025-06-18"

public val SUPPORTED_PROTOCOL_VERSIONS: List<String> = listOf(
    LATEST_PROTOCOL_VERSION,
    DEFAULT_NEGOTIATED_PROTOCOL_VERSION,
    "2025-03-26",
    "2024-11-05",
)

// ============================================================================
// Base Interfaces
// ============================================================================

/**
 * Represents an entity that includes additional metadata in its responses.
 */
@Serializable
public sealed interface WithMeta {
    @SerialName("_meta")
    public val meta: JsonObject?

    @Deprecated(
        message = "Use 'meta' instead.",
        replaceWith = ReplaceWith("meta"),
        level = DeprecationLevel.ERROR,
    )
    @Suppress("PropertyName", "VariableNaming")
    public val _meta: JsonObject
        get() = meta ?: EmptyJsonObject
}

// ============================================================================
// Tokens
// ============================================================================

/**
 * A progress token, used to associate progress notifications with the original request.
 */
public typealias ProgressToken = RequestId

public fun ProgressToken(value: String): ProgressToken = RequestId(value)

public fun ProgressToken(value: Long): ProgressToken = RequestId(value)

// ============================================================================
// Visual Elements
// ============================================================================

/**
 * An optionally sized icon that can be displayed in a user interface.
 *
 * Icons help clients provide visual branding and identification for MCP implementations.
 *
 * **Security considerations:**
 * - Consumers SHOULD ensure URLs serving icons are from the same domain as the client/server
 *   or a trusted domain to prevent malicious content.
 * - Consumers SHOULD take appropriate precautions when rendering SVGs as they can contain
 *   executable JavaScript. Consider sanitizing SVG content or rendering in isolated contexts.
 *
 * @property src A standard URI pointing to an icon resource.
 * Maybe an HTTP/HTTPS URL or a data: URI with Base64-encoded image data.
 * Example: "https://example.com/icon.png" or "data:image/png;base64,iVBORw0KG..."
 * @property mimeType Optional MIME type override if the source MIME type is missing or generic.
 * For example, "image/png", "image/jpeg", or "image/svg+xml".
 * Useful when the URL doesn't include a file extension or uses a generic MIME type.
 * @property sizes Optional array of strings that specify sizes at which the icon can be used.
 * Each string should be in WxH format (e.g., "48x48", "96x96") or "any" for
 * scalable formats like SVG. If not provided, the client should assume that
 * the icon can be used at any size.
 * @property theme Optional specifier for the theme this icon is designed for.
 * [Theme.Light] indicates the icon is designed for a light background,
 * [Theme.Dark] indicates the icon is designed for a dark background.
 * If not provided, the client should assume the icon can be used with any theme.
 */
@Serializable
public data class Icon(
    val src: String,
    val mimeType: String? = null,
    val sizes: List<String>? = null,
    val theme: Theme? = null,
) {
    /**
     * The theme context for which an icon is designed.
     *
     * @property Light Icon designed for use with a light background (typically darker icon).
     * @property Dark Icon designed for use with a dark background (typically lighter icon).
     */
    @Serializable
    public enum class Theme {
        /** Icon designed for use with a light background */
        @SerialName("light")
        Light,

        /** Icon designed for use with a dark background */
        @SerialName("dark")
        Dark,
    }
}

// ============================================================================
// Roles and References
// ============================================================================

/**
 * The sender or recipient of messages and data in a conversation.
 */
@Serializable
public enum class Role {
    @SerialName("user")
    User,

    @SerialName("assistant")
    Assistant,
}

/**
 * Base interface for reference types in the protocol.
 *
 * References are used to point to other entities (prompts, resources, etc.)
 * without including their full definitions.
 */
@Serializable(with = ReferencePolymorphicSerializer::class)
public sealed interface Reference {
    public val type: ReferenceType
}

@Serializable
public enum class ReferenceType(public val value: String) {
    @SerialName("ref/prompt")
    Prompt("ref/prompt"),

    @SerialName("ref/resource")
    ResourceTemplate("ref/resource"),
}

// ============================================================================
// Annotations and Metadata
// ============================================================================

/**
 * Optional annotations for the client.
 *
 * The client can use annotations to inform how objects are used or displayed.
 *
 * @property audience Describes who the intended customer of this object or data is.
 * Can include multiple entries to indicate content useful for multiple audiences
 * (e.g., [Role.user, Role.assistant]).
 * @property priority Describes how important this data is for operating the server.
 * A value of 1.0 means "most important" and indicates that the data is effectively required,
 * while 0.0 means "least important" and indicates that the data is entirely optional.
 * Should be a value between 0.0 and 1.0.
 * @property lastModified The moment the resource was last modified, as an ISO 8601 formatted string
 *  (e.g., "2025-01-12T15:00:58Z").
 *  Examples: last activity timestamp in an open file, timestamp when the resource was attached, etc.
 */
@Serializable
public data class Annotations(
    val audience: List<Role>? = null,
    val priority: Double? = null,
    val lastModified: String? = null,
) {
    init {
        require(priority == null || priority in 0.0..1.0) { "Priority must be between 0.0 and 1.0" }
    }
}
