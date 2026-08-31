package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmInline

// ============================================================================
// Protocol Version Constants
// ============================================================================

/**
 * Every released MCP protocol revision, ordered oldest to newest.
 *
 * Position in this list is the ordering [isVersionAtLeast] uses; revision identifiers are never
 * compared as strings.
 */
public val KNOWN_PROTOCOL_VERSIONS: List<String> = listOf(
    "2024-11-05",
    "2025-03-26",
    "2025-06-18",
    "2025-11-25",
    "2026-07-28",
)

/**
 * The revisions reachable through the `initialize` handshake.
 *
 * A peer speaking one of these establishes its version, capabilities and identity once, and every
 * later request on the connection inherits them.
 */
public val HANDSHAKE_PROTOCOL_VERSIONS: List<String> = listOf(
    "2024-11-05",
    "2025-03-26",
    "2025-06-18",
    "2025-11-25",
)

/**
 * The revisions that carry the per-request `_meta` envelope.
 *
 * These have no handshake: every request states its own version, capabilities and identity, so a
 * receiver serves it without consulting anything an earlier request established.
 */
public val MODERN_PROTOCOL_VERSIONS: List<String> = listOf("2026-07-28")

/** The newest protocol revision this SDK speaks, in either lifecycle. */
public const val LATEST_PROTOCOL_VERSION: String = "2026-07-28"

/**
 * The newest revision reachable through `initialize`.
 *
 * This is what a client offers in the handshake, and what a server counter-offers when it cannot
 * meet the requested version.
 */
public const val LATEST_HANDSHAKE_VERSION: String = "2025-11-25"

/** The newest envelope revision; the version a `server/discover` probe asks for first. */
public const val LATEST_MODERN_VERSION: String = "2026-07-28"

/** The default protocol version used when negotiation is not performed. */
public const val DEFAULT_NEGOTIATED_PROTOCOL_VERSION: String = "2025-03-26"

/** All MCP protocol versions supported by this SDK. */
@Deprecated(
    "Prefer HANDSHAKE_PROTOCOL_VERSIONS or MODERN_PROTOCOL_VERSIONS. " +
        "This union cannot express which lifecycle a version belongs to.",
    ReplaceWith("HANDSHAKE_PROTOCOL_VERSIONS"),
    DeprecationLevel.WARNING,
)
public val SUPPORTED_PROTOCOL_VERSIONS: List<String> =
    HANDSHAKE_PROTOCOL_VERSIONS + MODERN_PROTOCOL_VERSIONS

/**
 * Whether [version] is a known revision at least as new as [minimum].
 *
 * Ordering is by position in [KNOWN_PROTOCOL_VERSIONS], never lexicographic: revision identifiers
 * are not guaranteed to stay date-shaped, and an unrecognized peer string has to compare
 * conservatively rather than accidentally — `"zzz" > "2025-11-25"` holds for strings and is wrong
 * for protocol versions. An unknown [version] therefore returns `false`.
 *
 * @throws IllegalArgumentException if [minimum] is not a known revision
 */
public fun isVersionAtLeast(version: String, minimum: String): Boolean {
    val minimumIndex = KNOWN_PROTOCOL_VERSIONS.indexOf(minimum)
    require(minimumIndex >= 0) { "Unknown protocol version: $minimum" }
    return KNOWN_PROTOCOL_VERSIONS.indexOf(version) >= minimumIndex
}

/**
 * Whether [version] carries the per-request `_meta` envelope.
 *
 * Membership, not a range test: a revision belongs to a lifecycle by declaration, and a future
 * revision that returns to the handshake must not be classified by being newer than this one.
 */
public fun isModernProtocolVersion(version: String): Boolean = version in MODERN_PROTOCOL_VERSIONS

// ============================================================================
// Base Interfaces
// ============================================================================

/**
 * Represents an entity that includes additional metadata in its responses.
 */
@Serializable
public sealed interface WithMeta {
    /** Optional metadata attached to this entity. */
    @SerialName("_meta")
    public val meta: JsonObject?
}

/**
 * Metadata attached to a result's `_meta` field.
 *
 * Counterpart of [RequestMeta] on the response side. Reserved protocol keys are read through
 * dedicated accessors; everything else stays reachable via [get] and [json].
 *
 * @property json the raw JSON object containing the metadata
 */
@JvmInline
@Serializable
public value class ResultMeta(public val json: JsonObject) {
    /**
     * Retrieves the value associated with the specified key from the JSON object.
     *
     * @param key the key whose corresponding value is to be returned
     * @return the JsonElement associated with the specified key, or null if the key does not exist
     */
    public operator fun get(key: String): JsonElement? = json[key]
}

// ============================================================================
// Tokens
// ============================================================================

/**
 * A progress token, used to associate progress notifications with the original request.
 */
public typealias ProgressToken = RequestId

/** Creates a [ProgressToken] from a string value. */
public fun ProgressToken(value: String): ProgressToken = RequestId(value)

/** Creates a [ProgressToken] from a numeric value. */
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
    /** Discriminator identifying the reference subtype. */
    public val type: ReferenceType
}

/**
 * Discriminator for [Reference] subtypes used in completion and other operations.
 *
 * @property value serialized string representation of this reference type
 */
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
