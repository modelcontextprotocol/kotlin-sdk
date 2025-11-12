package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a root directory or file that the server can operate on.
 *
 * Roots define the top-level locations that a server has access to. They act as
 * boundaries for the server's operations, ensuring it only accesses permitted locations.
 *
 * @property uri The URI identifying the root. This **must** start with `file://` for now.
 * This restriction may be relaxed in future versions of the protocol to allow other URI schemes.
 * Examples: "file:///home/user/projects", "file:///workspace/repo", "file:///C:/Documents"
 * @property name An optional name for the root. This can be used to provide a human-readable
 * identifier for the root, which may be useful for display purposes or for
 * referencing the root in other parts of the application.
 * @property meta Optional metadata for this root.
 */
@Serializable
public data class Root(
    val uri: String,
    val name: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : WithMeta {
    init {
        require(uri.startsWith("file://")) {
            "Root URI must start with 'file://', got: $uri"
        }
    }
}

// ============================================================================
// roots/list
// ============================================================================

/**
 * Sent from the server to request a list of root URIs from the client.
 *
 * Roots allow servers to ask for specific directories or files to operate on.
 * A common example for roots is providing a set of repositories or directories
 * a server should operate on.
 *
 * This request is typically used when the server needs to understand the file system
 * structure or access specific locations that the client has permission to read from.
 *
 * **Note:** Unlike most other requests, this is sent from the **server to the client**,
 * not from client to server. The client must support the `roots` capability with
 * `listChanged = true` to receive these requests.
 *
 * @property params Optional request parameters containing metadata.
 */
@Serializable
public data class ListRootsRequest(override val params: BaseRequestParams? = null) : ServerRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.RootsList

    public val meta: RequestMeta?
        get() = params?.meta

    @Deprecated(
        message = "Use the constructor with BaseRequestParams property instead",
        replaceWith = ReplaceWith("ListRootsRequest(BaseRequestParams(meta))"),
        level = DeprecationLevel.WARNING,
    )
    public constructor(
        meta: RequestMeta?,
    ) : this(BaseRequestParams(meta))
}

/**
 * The client's response to a [ListRootsRequest] from the server.
 *
 * This result contains an array of Root objects, each representing a root directory
 * or file that the server can operate on. Roots define the boundaries of what
 * the server is allowed to access.
 *
 * @property roots The list of root URIs that the server can access.
 *                Each root represents a top-level directory, file, or other location
 *                that the server has permission to work with.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ListRootsResult(
    val roots: List<Root>,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult
