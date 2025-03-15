package io.modelcontextprotocol.sample.client

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) throw IllegalArgumentException("Usage: java -jar <your_path>/build/libs/kotlin-mcp-client-0.1.0.jar <path_to_server_script>")
    val serverPath = args.first()
        args.firstOrNull()?:throw IllegalArgumentException("Server path must be provided as first argument")
    val client = MCPClient()
    client.connectToServer(serverPath)
    println("Connected to server")
}