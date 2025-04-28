package io.modelcontextprotocol.sample.client

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) throw IllegalArgumentException("Usage: java -jar <your_path>/build/libs/kotlin-mcp-client-0.1.0-all.jar <path_to_server_script>")
    val serverPath = args.first()
    val client = MCPClient()
    client.use {
        client.connectToServer(serverPath)
        println("starting mcp client")
        client.chatLoop()
    }
}


//fun main(args: Array<String>) = runBlocking {
//    if (args.isEmpty()) {
//        println("Please provide the path to the MCP server script as a command-line argument.")
//        return@runBlocking
//    }
//
//    val serverScriptPath = args[0]
//    MCPClient().use { client ->
//        try {
//            client.connectToServer(serverScriptPath)
//            client.chatLoop()
//        } catch (e: Exception) {
//            println("Error: ${e.message}")
//            e.printStackTrace()
//        }
//    }
//}


//fun main() = runBlocking {
//    val client = MCPClient()
//    client.connectToServer(System.getenv("SERVER_PATH")!!)
//    client.chatLoop()
//}

