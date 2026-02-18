package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi

/**
 * Creates a [SetLevelRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [loggingLevel][SetLevelRequestBuilder.loggingLevel] - The logging level to set
 *
 * ## Optional
 * - [meta][SetLevelRequestBuilder.meta] - Metadata for the request
 *
 * Example setting info level:
 * ```kotlin
 * val request = SetLevelRequest {
 *     loggingLevel = LoggingLevel.Info
 * }
 * ```
 *
 * Example setting debug level:
 * ```kotlin
 * val request = SetLevelRequest {
 *     loggingLevel = LoggingLevel.Debug
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the logging level request
 * @return A configured [SetLevelRequest] instance
 * @see SetLevelRequestBuilder
 * @see SetLevelRequest
 * @see LoggingLevel
 */
@ExperimentalMcpApi
public inline operator fun SetLevelRequest.Companion.invoke(block: SetLevelRequestBuilder.() -> Unit): SetLevelRequest =
    SetLevelRequestBuilder().apply(block).build()

/**
 * DSL builder for constructing [SetLevelRequest] instances.
 *
 * This builder creates requests to set the logging level for the server.
 *
 * ## Required
 * - [loggingLevel] - The logging level to set
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see SetLevelRequest
 * @see LoggingLevel
 */
@McpDsl
public class SetLevelRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The logging level to set. This is a required field.
     *
     * Available levels (from least to most severe):
     * - [LoggingLevel.Debug]
     * - [LoggingLevel.Info]
     * - [LoggingLevel.Notice]
     * - [LoggingLevel.Warning]
     * - [LoggingLevel.Error]
     * - [LoggingLevel.Critical]
     * - [LoggingLevel.Alert]
     * - [LoggingLevel.Emergency]
     *
     * Example: `loggingLevel = LoggingLevel.Warning`
     */
    public var loggingLevel: LoggingLevel? = null

    @PublishedApi
    override fun build(): SetLevelRequest {
        val level = requireNotNull(loggingLevel) {
            "Missing required field 'loggingLevel'. Example: loggingLevel = LoggingLevel.Info"
        }

        val params = SetLevelRequestParams(level = level, meta = meta)
        return SetLevelRequest(params = params)
    }
}
