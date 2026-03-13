package io.modelcontextprotocol.kotlin.sdk.conformance

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.CompleteResult
import io.modelcontextprotocol.kotlin.sdk.types.Method

fun Server.registerConformanceCompletions() {
    onConnect {
        val session = sessions.values.lastOrNull() ?: return@onConnect
        session.setRequestHandler<CompleteRequest>(Method.Defined.CompletionComplete) { _, _ ->
            CompleteResult(CompleteResult.Completion(values = emptyList(), total = 0, hasMore = false))
        }
    }
}
