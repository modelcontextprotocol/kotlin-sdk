@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The key used in `_meta` to associate a message with a task.
 */
public const val RELATED_TASK_META_KEY: String = "io.modelcontextprotocol/related-task"

/**
 * Common fields shared by all types representing the task state.
 */
public sealed interface TaskFields {
    public val taskId: String
    public val status: TaskStatus
    public val statusMessage: String?
    public val createdAt: String
    public val lastUpdatedAt: String
    public val ttl: Double?
    public val pollInterval: Double?
}

/**
 * Data associated with a task.
 *
 * @property taskId The task identifier.
 * @property status Current task state.
 * @property statusMessage Optional human-readable message describing the current task state.
 * This can provide context for any status, including reasons for "cancelled" status,
 * summaries for "completed" status, or diagnostic information for "failed" status.
 * @property createdAt ISO 8601 timestamp when the task was created.
 * @property lastUpdatedAt ISO 8601 timestamp when the task was last updated.
 * @property ttl Actual retention duration from creation in milliseconds, null for unlimited.
 * @property pollInterval Suggested polling interval in milliseconds.
 */
@Serializable
public data class Task(
    override val taskId: String,
    override val status: TaskStatus,
    override val statusMessage: String? = null,
    override val createdAt: String,
    override val lastUpdatedAt: String,
    override val ttl: Double?,
    override val pollInterval: Double? = null,
) : TaskFields

/**
 * Metadata for augmenting a request with task execution.
 * Include this in the `task` field of the request parameters.
 *
 * @property ttl Requested duration in milliseconds to retain task from creation.
 */
@Serializable
public data class TaskMetadata(val ttl: Double? = null)

/**
 * The status of a task.
 */
@Serializable
public enum class TaskStatus {
    @SerialName("working")
    Working,

    @SerialName("input_required")
    InputRequired,

    @SerialName("completed")
    Completed,

    @SerialName("failed")
    Failed,

    @SerialName("cancelled")
    Cancelled,
}

/**
 * Metadata for associating messages with a task.
 * Include this in the `_meta` field under the key `io.modelcontextprotocol/related-task`.
 *
 * @property taskId The task identifier this message is associated with.
 */
@Serializable
public data class RelatedTaskMetadata(val taskId: String)

/**
 * A response to a task-augmented request.
 *
 * @property task The task data associated with the response.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class CreateTaskResult(
    val task: Task,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult,
    ServerResult

// ============================================================================
// tasks/get
// ============================================================================

/**
 * A request to retrieve the state of a task.
 *
 * This request can be sent by either side (both [ClientRequest] and [ServerRequest]).
 *
 * @property params The parameters specifying which task to retrieve.
 */
@Serializable
public data class GetTaskRequest(override val params: GetTaskRequestParams) :
    ClientRequest,
    ServerRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.TasksGet

    /**
     * The task identifier to query.
     */
    public val taskId: String
        get() = params.taskId

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a tasks/get request.
 *
 * @property taskId The task identifier to query.
 * @property meta Optional metadata for this request.
 */
@Serializable
public data class GetTaskRequestParams(
    val taskId: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * The response to a tasks/get request.
 *
 * Contains all [Task] fields flattened into the result (intersection of Result and Task).
 *
 * @property taskId The task identifier.
 * @property status Current task state.
 * @property statusMessage Optional human-readable message describing the current task state.
 * @property createdAt ISO 8601 timestamp when the task was created.
 * @property lastUpdatedAt ISO 8601 timestamp when the task was last updated.
 * @property ttl Actual retention duration from creation in milliseconds, null for unlimited.
 * @property pollInterval Suggested polling interval in milliseconds.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class GetTaskResult(
    override val taskId: String,
    override val status: TaskStatus,
    override val statusMessage: String? = null,
    override val createdAt: String,
    override val lastUpdatedAt: String,
    override val ttl: Double?,
    override val pollInterval: Double? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult,
    ServerResult,
    TaskFields

// ============================================================================
// tasks/result
// ============================================================================

/**
 * A request to retrieve the result of a completed task.
 *
 * This request can be sent by either side (both [ClientRequest] and [ServerRequest]).
 *
 * @property params The parameters specifying which task to retrieve results for.
 */
@Serializable
public data class GetTaskPayloadRequest(override val params: GetTaskPayloadRequestParams) :
    ClientRequest,
    ServerRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.TasksResult

    /**
     * The task identifier to retrieve results for.
     */
    public val taskId: String
        get() = params.taskId

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a tasks/result request.
 *
 * @property taskId The task identifier to retrieve results for.
 * @property meta Optional metadata for this request.
 */
@Serializable
public data class GetTaskPayloadRequestParams(
    val taskId: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * The response to a tasks/result request.
 *
 * The structure matches the result type of the original request.
 * For example, a tools/call task would return the [CallToolResult] structure.
 *
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class GetTaskPayloadResult(
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult

// ============================================================================
// tasks/list
// ============================================================================

/**
 * A request to retrieve a list of tasks.
 *
 * This request can be sent by either side (both [ClientRequest] and [ServerRequest]).
 * Supports pagination through the cursor parameter.
 *
 * @property params Optional pagination parameters to control which page of results to return.
 */
@Serializable
public data class ListTasksRequest(override val params: PaginatedRequestParams? = null) :
    ClientRequest,
    ServerRequest,
    PaginatedRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.TasksList
}

/**
 * The response to a tasks/list request.
 *
 * @property tasks The list of tasks.
 * @property nextCursor An opaque token representing the pagination position after the last returned result.
 * If present, there may be more results available.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ListTasksResult(
    val tasks: List<Task>,
    override val nextCursor: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult,
    ServerResult,
    PaginatedResult

// ============================================================================
// tasks/cancel
// ============================================================================

/**
 * A request to cancel a task.
 *
 * This request can be sent by either side (both [ClientRequest] and [ServerRequest]).
 *
 * @property params The parameters specifying which task to cancel.
 */
@Serializable
public data class CancelTaskRequest(override val params: CancelTaskRequestParams) :
    ClientRequest,
    ServerRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.TasksCancel

    /**
     * The task identifier to cancel.
     */
    public val taskId: String
        get() = params.taskId

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a tasks/cancel request.
 *
 * @property taskId The task identifier to cancel.
 * @property meta Optional metadata for this request.
 */
@Serializable
public data class CancelTaskRequestParams(
    val taskId: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * The response to a tasks/cancel request.
 *
 * Contains all [Task] fields flattened into the result (intersection of Result and Task).
 *
 * @property taskId The task identifier.
 * @property status Current task state.
 * @property statusMessage Optional human-readable message describing the current task state.
 * @property createdAt ISO 8601 timestamp when the task was created.
 * @property lastUpdatedAt ISO 8601 timestamp when the task was last updated.
 * @property ttl Actual retention duration from creation in milliseconds, null for unlimited.
 * @property pollInterval Suggested polling interval in milliseconds.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class CancelTaskResult(
    override val taskId: String,
    override val status: TaskStatus,
    override val statusMessage: String? = null,
    override val createdAt: String,
    override val lastUpdatedAt: String,
    override val ttl: Double?,
    override val pollInterval: Double? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ClientResult,
    ServerResult,
    TaskFields
