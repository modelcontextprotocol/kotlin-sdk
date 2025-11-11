package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The severity of a log message.
 *
 * These levels map to syslog message severities, as specified in
 * [RFC-5424](https://datatracker.ietf.org/doc/html/rfc5424#section-6.2.1).
 *
 * Levels are ordered from least to most severe:
 * [Debug] < [Info] < [Notice] < [Warning] < [Error] < [Critical] < [Alert] < [Emergency]
 */
@Serializable
public enum class LoggingLevel {
    /** Detailed debug information for troubleshooting (RFC-5424: Debug) */
    @SerialName("debug")
    Debug,

    /** Informational messages about normal operations (RFC-5424: Informational) */
    @SerialName("info")
    Info,

    /** Normal but significant conditions (RFC-5424: Notice) */
    @SerialName("notice")
    Notice,

    /** Warning conditions that may require attention (RFC-5424: Warning) */
    @SerialName("warning")
    Warning,

    /** Error conditions that require attention (RFC-5424: Error) */
    @SerialName("error")
    Error,

    /** Critical conditions requiring immediate action (RFC-5424: Critical) */
    @SerialName("critical")
    Critical,

    /** Action must be taken immediately (RFC-5424: Alert) */
    @SerialName("alert")
    Alert,

    /** System is unusable, highest severity (RFC-5424: Emergency) */
    @SerialName("emergency")
    Emergency,
}

/**
 * A request from the client to the server to enable or adjust logging.
 *
 * After receiving this request, the server should send log messages at the specified
 * [level][SetLevelRequestParams.level] and higher (more severe) to the client as
 * notifications/message events.
 *
 * @property params The parameters specifying the desired logging level.
 */
@Serializable
public data class SetLevelRequest(override val params: SetLevelRequestParams) : ClientRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.LoggingSetLevel

    /**
     * The minimum severity level of logging that the client wants to receive from the server.
     */
    public val level: LoggingLevel
        get() = params.level

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a logging/setLevel request.
 *
 * @property level The minimum severity level of logging that the client wants to receive
 * from the server. The server should send all logs at this level and higher
 * (i.e., more severe) to the client as notifications/message.
 * For example, if [level] is [LoggingLevel.Warning], the server should send
 * warning, error, critical, alert, and emergency messages, but not info, notice, or debug.
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class SetLevelRequestParams(
    val level: LoggingLevel,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams
