package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [CompleteRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [argument][CompleteRequestBuilder.argument] - Sets the argument name and value to complete
 * - [ref][CompleteRequestBuilder.ref] - Sets the reference to a prompt or resource template
 *
 * ## Optional
 * - [context][CompleteRequestBuilder.context] - Adds additional context for the completion
 * - [meta][CompleteRequestBuilder.meta] - Adds metadata to the request
 *
 * Example with [PromptReference]:
 * ```kotlin
 * val request = buildCompleteRequest {
 *     argument("query", "user input")
 *     ref(PromptReference("searchPrompt"))
 * }
 * ```
 *
 * Example with [ResourceTemplateReference]:
 * ```kotlin
 * val request = buildCompleteRequest {
 *     argument("path", "/users/123")
 *     ref(ResourceTemplateReference("file:///{path}"))
 *     context {
 *         put("userId", "123")
 *         put("role", "admin")
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the completion request
 * @return A configured [CompleteRequest] instance
 * @see CompleteRequestBuilder
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
public inline fun buildCompleteRequest(block: CompleteRequestBuilder.() -> Unit): CompleteRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return CompleteRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [CompleteRequest] instances.
 *
 * This builder provides methods to configure completion requests for prompts or resource templates.
 * Both [argument] and [ref] are required; [context] is optional.
 *
 * @see buildCompleteRequest
 */
@McpDsl
public class CompleteRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    private var arg: CompleteRequestParams.Argument? = null
    private var ref: Reference? = null
    private var ctx: CompleteRequestParams.Context? = null

    /**
     * Sets the argument for completion.
     *
     * This method specifies the name and value of the argument to be completed.
     * This is a required field and must be called exactly once.
     *
     * Example:
     * ```kotlin
     * completeRequest {
     *     argument("query", "SELECT * FROM use")
     *     // ... other configuration
     * }
     * ```
     *
     * @param name The name of the argument to complete
     * @param value The partial or full value of the argument
     */
    public fun argument(name: String, value: String) {
        arg = CompleteRequestParams.Argument(name, value)
    }

    /**
     * Sets the reference to a prompt or resource template.
     *
     * This method specifies which prompt or resource template the completion request refers to.
     *
     * Example with prompt:
     * ```kotlin
     * completeRequest {
     *     ref(PromptReference("sqlQuery", "SQL Query Builder"))
     *     // ... other configuration
     * }
     * ```
     *
     * Example with resource template:
     * ```kotlin
     * completeRequest {
     *     ref(ResourceTemplateReference("file:///{path}"))
     *     // ... other configuration
     * }
     * ```
     *
     * @param value The [Reference] (either [PromptReference] or [ResourceTemplateReference])
     */
    public fun ref(value: Reference) {
        ref = value
    }

    /**
     * Sets additional context for the completion request using a Map.
     *
     * This method allows providing additional key-value pairs that may be relevant
     * for generating completions. This is an optional field.
     *
     * Example:
     * ```kotlin
     * completeRequest {
     *     context(mapOf("userId" to "123", "role" to "admin"))
     *     // ... other configuration
     * }
     * ```
     *
     * @param arguments A map of context key-value pairs
     */
    public fun context(arguments: Map<String, String>) {
        ctx = CompleteRequestParams.Context(arguments)
    }

    /**
     * Sets additional context for the completion request using a DSL builder.
     *
     * This method allows providing additional key-value pairs that may be relevant
     * for generating completions using a type-safe builder syntax. This is an optional field.
     *
     * Example:
     * ```kotlin
     * completeRequest {
     *     context {
     *         put("userId", "123")
     *         put("role", "admin")
     *         put("environment", "production")
     *     }
     *     // ... other configuration
     * }
     * ```
     *
     * @param block Lambda with receiver for building the context map
     */
    public fun context(block: MutableMap<String, String>.() -> Unit) {
        ctx = CompleteRequestParams.Context(buildMap(block))
    }

    @PublishedApi
    override fun build(): CompleteRequest {
        val argument = requireNotNull(arg) {
            "Missing required field 'argument(name, value)'. Example: argument(\"query\", \"user input\")"
        }

        val reference = requireNotNull(ref) {
            "Missing required field 'ref(Reference)'. Use ref(PromptReference(\"name\")) " +
                "or ref(ResourceTemplateReference(\"uri\"))"
        }

        val params = CompleteRequestParams(argument = argument, ref = reference, context = ctx, meta = meta)
        return CompleteRequest(params)
    }
}
