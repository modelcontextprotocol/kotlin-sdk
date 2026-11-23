@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

// ============================================================================
// Skills extension (SEP-2640): https://modelcontextprotocol.io/extensions/skills/overview
// ============================================================================

/** Identifier of the Skills extension, used as a key in [ServerCapabilities.extensions]. */
@ExperimentalMcpApi
public const val SKILLS_EXTENSION_ID: String = "io.modelcontextprotocol/skills"

/**
 * Settings a server declares for the Skills extension under [SKILLS_EXTENSION_ID].
 *
 * A server that declares the extension must also declare [ServerCapabilities.resources],
 * because skill files are read through `resources/read`.
 *
 * @property directoryRead whether the server supports `resources/directory/read` for skill directories
 */
@Serializable
@ExperimentalMcpApi
public data class SkillsExtensionSettings(val directoryRead: Boolean? = null)

/**
 * Returns these capabilities with the Skills extension declared using [settings].
 *
 * Also declares an empty [ServerCapabilities.resources] when none is present, since the extension requires it.
 */
@ExperimentalMcpApi
public fun ServerCapabilities.withSkillsExtension(
    settings: SkillsExtensionSettings = SkillsExtensionSettings(),
): ServerCapabilities = copy(
    resources = resources ?: ServerCapabilities.Resources(),
    extensions = extensions.orEmpty() + (SKILLS_EXTENSION_ID to McpJson.encodeToJsonElement(settings).jsonObject),
)

/** The Skills extension settings declared by the server, or `null` if it does not declare the extension. */
@ExperimentalMcpApi
public val ServerCapabilities.skillsExtension: SkillsExtensionSettings?
    get() = extensions?.get(SKILLS_EXTENSION_ID)?.let { McpJson.decodeFromJsonElement(it) }

/**
 * A file belonging to a skill, with the digest and size of its raw content.
 *
 * @property uri resource URI of the file
 * @property digest SHA-256 digest of the file's raw bytes, formatted as `sha256:{64 lowercase hex characters}`
 * @property size length in bytes of the file's raw content
 * @throws IllegalArgumentException if [digest] is malformed or [size] is negative
 */
@Serializable
@ExperimentalMcpApi
public data class SkillResource(val uri: String, val digest: String, val size: Long) {
    init {
        require(DIGEST_PATTERN.matches(digest)) { "digest must match sha256:{64 lowercase hex}, but was $digest" }
        require(size >= 0) { "size must be non-negative, but was $size" }
    }
}

private val DIGEST_PATTERN = Regex("^sha256:[0-9a-f]{64}$")

/**
 * The files of a skill: either a complete [Files] enumeration or [Dynamic].
 *
 * On the wire this is an array of [SkillResource] objects or the string `"dynamic"`.
 */
@Serializable(with = SkillResourcesSerializer::class)
@ExperimentalMcpApi
public sealed interface SkillResources {
    /**
     * A complete enumeration of `SKILL.md` and every supporting file.
     *
     * @property files every file of the skill, each listed once
     */
    public data class Files(val files: List<SkillResource>) : SkillResources

    /** The skill's content is generated, so stable digests cannot be published. */
    public data object Dynamic : SkillResources
}

@OptIn(ExperimentalMcpApi::class)
internal object SkillResourcesSerializer : KSerializer<SkillResources> {
    private const val DYNAMIC = "dynamic"
    private val filesSerializer = ListSerializer(SkillResource.serializer())

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SkillResources) {
        val json = encoder as? JsonEncoder
            ?: throw SerializationException("SkillResourcesSerializer requires a Json encoder")
        val element = when (value) {
            is SkillResources.Files -> json.json.encodeToJsonElement(filesSerializer, value.files)
            SkillResources.Dynamic -> JsonPrimitive(DYNAMIC)
        }
        json.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): SkillResources {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("SkillResourcesSerializer requires a Json decoder")
        val element = json.decodeJsonElement()
        return when {
            element is JsonArray -> SkillResources.Files(json.json.decodeFromJsonElement(filesSerializer, element))
            element is JsonPrimitive && element.isString && element.content == DYNAMIC -> SkillResources.Dynamic
            else -> throw SerializationException("skill resources must be an array or \"$DYNAMIC\", but was $element")
        }
    }
}

/**
 * The entry for a single skill, as returned by `skills/list` and `skills/get`.
 *
 * @property uri resource URI of the skill's `SKILL.md`, readable via `resources/read`
 * @property frontmatter the skill's `SKILL.md` YAML frontmatter rendered verbatim as a JSON object;
 * `name` and `description` are always present and other fields pass through unchanged
 * @property resources the skill's files, or [SkillResources.Dynamic]
 * @property meta optional metadata; keys defined by this extension use the `io.modelcontextprotocol.skills/` prefix
 * @throws IllegalArgumentException if [frontmatter] lacks a string `name` or `description`
 */
@Serializable
@ExperimentalMcpApi
public data class Skill(
    val uri: String,
    val frontmatter: JsonObject,
    val resources: SkillResources,
    @SerialName("_meta")
    val meta: JsonObject? = null,
) {
    init {
        requireNotNull(frontmatter.stringField("name")) { "frontmatter.name must be a string" }
        requireNotNull(frontmatter.stringField("description")) { "frontmatter.description must be a string" }
    }

    /** The skill name from [frontmatter]. */
    public val name: String
        get() = requireNotNull(frontmatter.stringField("name"))

    /** The skill description from [frontmatter]. */
    public val description: String
        get() = requireNotNull(frontmatter.stringField("description"))
}

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

// ============================================================================
// skills/list
// ============================================================================

/**
 * Lists the skills a server serves. Supports pagination.
 *
 * @property params optional pagination parameters
 */
@Serializable
@ExperimentalMcpApi
public data class ListSkillsRequest(override val params: PaginatedRequestParams? = null) :
    ClientRequest,
    PaginatedRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.SkillsList
}

/**
 * The server's response to a [ListSkillsRequest].
 *
 * @property skills the skills on this page
 * @property nextCursor opaque token for the next page, or `null` if there are no more results
 * @property resultType discriminator for the result representation
 * @property ttlMs number of milliseconds clients may treat this result as fresh
 * @property cacheScope authorization boundary within which the result may be reused
 * @property meta optional response metadata
 * @throws IllegalArgumentException if [ttlMs] is negative
 */
@Serializable
@ExperimentalMcpApi
public data class ListSkillsResult(
    val skills: List<Skill>,
    override val nextCursor: String? = null,
    @EncodeDefault
    val resultType: String = COMPLETE_RESULT_TYPE,
    @Required
    val ttlMs: Long = 0,
    @Required
    val cacheScope: CacheScope = CacheScope.Private,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult,
    PaginatedResult {
    init {
        require(ttlMs >= 0) { "ttlMs must be non-negative, but was $ttlMs" }
    }
}

// ============================================================================
// skills/get
// ============================================================================

/**
 * Parameters for a `skills/get` request.
 *
 * @property uri URI of the skill's `SKILL.md`
 * @property meta optional request metadata
 */
@Serializable
@ExperimentalMcpApi
public data class GetSkillRequestParams(
    val uri: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * Gets the entry for one skill by the URI of its `SKILL.md`.
 *
 * @property params the skill to get
 */
@Serializable
@ExperimentalMcpApi
public data class GetSkillRequest(override val params: GetSkillRequestParams) : ClientRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.SkillsGet

    /** URI of the skill's `SKILL.md`. */
    public val uri: String
        get() = params.uri
}

/**
 * The server's response to a [GetSkillRequest].
 *
 * @property skill the requested skill
 * @property resultType discriminator for the result representation
 * @property ttlMs number of milliseconds clients may treat this result as fresh
 * @property cacheScope authorization boundary within which the result may be reused
 * @property meta optional response metadata
 * @throws IllegalArgumentException if [ttlMs] is negative
 */
@Serializable
@ExperimentalMcpApi
public data class GetSkillResult(
    val skill: Skill,
    @EncodeDefault
    val resultType: String = COMPLETE_RESULT_TYPE,
    @Required
    val ttlMs: Long = 0,
    @Required
    val cacheScope: CacheScope = CacheScope.Private,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult {
    init {
        require(ttlMs >= 0) { "ttlMs must be non-negative, but was $ttlMs" }
    }
}
