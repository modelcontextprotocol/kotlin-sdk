package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Payload carried by the [data][RPCError.data] of a [RPCError.ErrorCode.URL_ELICITATION_REQUIRED] error.
 *
 * @property elicitations The URL-mode elicitations that must be completed before the original request
 *   can succeed. Each entry is equivalent to an `elicitation/create` request.
 */
@Serializable
public data class UrlElicitationRequiredData(val elicitations: List<ElicitRequestURLParams>)

/**
 * Signals that one or more URL-mode elicitations must be completed before a request can be processed.
 *
 * Serialized over the wire as a JSON-RPC error with code [RPCError.ErrorCode.URL_ELICITATION_REQUIRED]
 * and [data][RPCError.data] holding the [elicitations]. A server throws this from a request handler to
 * tell the client that a URL-mode elicitation is required; the client receives it from any request as a
 * typed exception, equivalent to receiving an `elicitation/create` request.
 *
 * The client is responsible for obtaining explicit user consent and surfacing each [ElicitRequestURLParams]
 * before navigation — the SDK never opens or validates URLs.
 *
 * Example client-side handling:
 * ```kotlin
 * try {
 *     client.callTool(name = "create_repo", arguments = args)
 * } catch (e: UrlElicitationRequiredException) {
 *     for (elicitation in e.elicitations) {
 *         // Show elicitation.message and the target domain, then ask the user for consent.
 *         if (userConsents(elicitation.url)) openInBrowser(elicitation.url)
 *         // Await completion via Client.setElicitationCompleteHandler, then optionally retry the call.
 *     }
 * }
 * ```
 *
 * @property elicitations The required URL-mode elicitations (always non-empty).
 */
public class UrlElicitationRequiredException(
    public val elicitations: List<ElicitRequestURLParams>,
    message: String = defaultMessage(elicitations),
) : McpException(
    code = RPCError.ErrorCode.URL_ELICITATION_REQUIRED,
    message = message,
    data = McpJson.encodeToJsonElement(UrlElicitationRequiredData(elicitations)),
) {
    internal companion object {
        private fun defaultMessage(elicitations: List<ElicitRequestURLParams>): String =
            if (elicitations.size > 1) "URL elicitations are required" else "URL elicitation is required"

        /**
         * Reconstructs a [UrlElicitationRequiredException] from JSON-RPC error [data], or `null` when the
         * payload is absent, malformed, or carries no elicitations. Never throws.
         */
        fun fromDataOrNull(message: String, data: JsonElement): UrlElicitationRequiredException? =
            runCatching { McpJson.decodeFromJsonElement<UrlElicitationRequiredData>(data).elicitations }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { UrlElicitationRequiredException(it, message) }
    }
}
