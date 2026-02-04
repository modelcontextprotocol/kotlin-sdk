// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleClientRoots01

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

suspend fun main() {

val client = Client(
    clientInfo = Implementation("demo-client", "1.0.0"),
    options = ClientOptions(
        capabilities = ClientCapabilities(roots = ClientCapabilities.Roots(listChanged = true)),
    ),
)

client.addRoot(
    uri = "file:///Users/demo/projects",
    name = "Projects",
)
client.sendRootsListChanged()
}    
