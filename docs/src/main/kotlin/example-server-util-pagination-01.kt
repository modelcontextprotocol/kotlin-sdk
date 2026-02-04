// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleServerUtilPagination01

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

suspend fun main() {

val server = Server(
    serverInfo = Implementation("example-server", "1.0.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(),
        ),
    )
)

val session = server.createSession(
    StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
)

val resources = listOf(
    Resource(uri = "note://1", name = "Note 1", description = "First"),
    Resource(uri = "note://2", name = "Note 2", description = "Second"),
    Resource(uri = "note://3", name = "Note 3", description = "Third"),
)
val pageSize = 2

session.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { request, _ ->
    val start = request.params?.cursor?.toIntOrNull() ?: 0
    val page = resources.drop(start).take(pageSize)
    val next = if (start + page.size < resources.size) (start + page.size).toString() else null

    ListResourcesResult(
        resources = page,
        nextCursor = next,
    )
}
}
