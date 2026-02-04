// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleServerUtilCompletions01

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.CompleteResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

suspend fun main() {

val server = Server(
    serverInfo = Implementation(
        name = "example-server",
        version = "1.0.0"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            completions = ServerCapabilities.Completions,
        ),
    )
)

val session = server.createSession(
    StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
)

session.setRequestHandler<CompleteRequest>(Method.Defined.CompletionComplete) { request, _ ->
    val options = listOf("kotlin", "compose", "coroutine")
    val matches = options.filter { it.startsWith(request.argument.value.lowercase()) }

    CompleteResult(
        completion = CompleteResult.Completion(
            values = matches.take(3),
            total = matches.size,
            hasMore = matches.size > 3,
        ),
    )
}
}
