package io.modelcontextprotocol.sample.client

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.optionals.getOrNull

class MCPClient : AutoCloseable {
    // Configures using the `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` environment variables
//    private val anthropic = AnthropicOkHttpClient.fromEnv()
////        .apiKey(System.getenv("ANTHROPIC_API_KEY") ?: "your_api_key_here")
////        .build()

    private val anthropic = AnthropicOkHttpClient.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY") )
        .build()

    // Initialize MCP client
    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))

    private val messageParamsBuilder: MessageCreateParams.Builder = MessageCreateParams.builder()
        .model(Model.CLAUDE_3_5_SONNET_20241022)
        .maxTokens(1024)

    // List of tools offered by the server
    private lateinit var tools: List<ToolUnion>


    private fun JsonObject.toJsonValue(): JsonValue {
        val mapper = ObjectMapper()
        val node = mapper.readTree(this.toString())
        return JsonValue.fromJsonNode(node)
    }

    // Connect to the server using the path to the server
    suspend fun connectToServer(serverScriptPath: String) {
        try {
            // Build the command based on the file extension of the server script
            val command = buildList {
                when (serverScriptPath.substringAfterLast(".")) {
                    "js" -> add("node")
                    "py" -> add(if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3")
                    "jar" -> addAll(listOf("java", "-jar"))
                    else -> throw IllegalArgumentException("Server script must be a .js, .py or .jar file")
                }
                add(serverScriptPath)
            }

            // Start the server process
            val process = ProcessBuilder(command).start()

            // Setup I/O transport using the process streams
            val transport = StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered()
            )

            // Connect the MCP client to the server using the transport
            mcp.connect(transport)

            // Request the list of available tools from the server
            val toolsResult = mcp.listTools()
            tools = toolsResult?.tools?.map { tool ->
                ToolUnion.ofTool(
                    Tool.builder()
                        .name(tool.name)
                        .description(tool.description ?: "")
                        .inputSchema(
                            Tool.InputSchema.builder()
                                .type(JsonValue.from(tool.inputSchema.type))
                                .properties(tool.inputSchema.properties.toJsonValue())
                                .putAdditionalProperty("required", JsonValue.from(tool.inputSchema.required))
                                .build()
                        )
                        .build()
                )
            } ?: emptyList()
            println("Connected to server with tools: ${tools.joinToString(", ") { it.tool().get().name() }}")
        } catch (e: Exception) {
            println("Failed to connect to MCP server: $e")
            throw e
        }
    }

    private val messages = mutableListOf<MessageParam>()

    // Process a user query and return a string response
    suspend fun processQuery(query: String): String {
        // Create an initial message with a user's query

//        val messages = mutableListOf(
//            MessageParam.builder()
//                .role(MessageParam.Role.USER)
//                .content(query)
//                .build()
//        )
        messages.add(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(query)
                .build()
        )

        // Send the query to the Anthropic model and get the response
        val response = anthropic.messages().create(
            messageParamsBuilder
                .messages(messages)
                .tools(tools)
                .build()
        )

//        val assistantReply = response.content().firstOrNull()?.text()?.getOrNull()?.text()


//        val finalText = mutableListOf<String>()
//        response.content().forEach { content ->
//            when {
//                // Append text outputs from the response
//                content.isText() -> finalText.add(content.text().getOrNull()?.text() ?: "")
//
//                // If the response indicates a tool use, process it further
//                content.isToolUse() -> {
//                    val toolName = content.toolUse().get().name()
//                    val toolArgs =
//                        content.toolUse().get()._input().convert(object : TypeReference<Map<String, JsonValue>>() {})
//
//                    // Call the tool with provided arguments
//                    val result = mcp.callTool(
//                        name = toolName,
//                        arguments = toolArgs ?: emptyMap()
//                    )
//                    finalText.add("[Calling tool $toolName with args $toolArgs]")
//
//                    // Add the tool result message to the conversation
//                    messages.add(
//                        MessageParam.builder()
//                            .role(MessageParam.Role.USER)
//                            .content(
//                                """
//                                        "type": "tool_result",
//                                        "tool_name": $toolName,
//                                        "result": ${result?.content?.joinToString("\n") { (it as TextContent).text ?: "" }}
//                                    """.trimIndent()
//                            )
//                            .build()
//                    )
//
//                    // Retrieve an updated response after tool execution
//                    val aiResponse = anthropic.messages().create(
//                        messageParamsBuilder
//                            .messages(messages)
//                            .build()
//                    )
//
//                    // Append the updated response to final text
//                    finalText.add(aiResponse.content().first().text().getOrNull()?.text() ?: "")
//                }
//            }
//        }
//
//        return finalText.joinToString("\n", prefix = "", postfix = "")
//    }
        val finalText = mutableListOf<String>()

        response.content().forEach { content ->
            when {
                // Append text outputs from the response
                content.isText() -> {
                    val text = content.text().getOrNull()?.text()
                    if (!text.isNullOrBlank()) {
                        finalText.add(text)

                        // Save assistant response to memory
                        messages.add(
                            MessageParam.builder()
                                .role(MessageParam.Role.ASSISTANT)
                                .content(text)
                                .build()
                        )
                    }
                }

                // If the response indicates a tool use, process it further
                content.isToolUse() -> {
                    val toolName = content.toolUse().get().name()
                    val toolArgs =
                        content.toolUse().get()._input().convert(object : TypeReference<Map<String, JsonValue>>() {})

                    // Call the tool with provided arguments
                    val result = mcp.callTool(
                        name = toolName,
                        arguments = toolArgs ?: emptyMap()
                    )

                    finalText.add("[Calling tool $toolName with args $toolArgs]")

                    // Add the tool_result to messages
                    val toolResultContent = """
            {
              "type": "tool_result",
              "tool_name": "$toolName",
              "result": "${result?.content?.joinToString("\n") { (it as TextContent).text ?: "" }}"
            }
            """.trimIndent()

                    messages.add(
                        MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(toolResultContent)
                            .build()
                    )

                    // Retrieve an updated response after tool execution
                    val aiResponse = anthropic.messages().create(
                        messageParamsBuilder
                            .messages(messages)
                            .build()
                    )

                    val aiReply = aiResponse.content().firstOrNull()?.text()?.getOrNull()?.text()
                    if (!aiReply.isNullOrBlank()) {
                        finalText.add(aiReply)

                        // Save assistant's new response after tool use
                        messages.add(
                            MessageParam.builder()
                                .role(MessageParam.Role.ASSISTANT)
                                .content(aiReply)
                                .build()
                        )
                    }
                }
            }
        }

//        println(messages)

        return finalText.joinToString("\n", prefix = "", postfix = "")
    }



        // Main chat loop for interacting with the user
    suspend fun chatLoop() {
        println("\nMCP Client Started!")
        println("Type your queries or 'quit' to exit.")

        while (true) {
            print("\nQuery: ")
            val message = readLine() ?: break
            if (message.lowercase() == "quit") break
            val response = processQuery(message)
            println("\n$response")
        }
    }

    override fun close() {
        runBlocking {
            mcp.close()
            anthropic.close()
        }
    }
}


//
//
//package io.modelcontextprotocol.sample.client
//
//import com.anthropic.client.okhttp.AnthropicOkHttpClient
//import com.anthropic.core.JsonValue
//import com.anthropic.models.messages.*
//import com.fasterxml.jackson.core.type.TypeReference
//import com.fasterxml.jackson.databind.ObjectMapper
//import io.modelcontextprotocol.kotlin.sdk.Implementation
//import io.modelcontextprotocol.kotlin.sdk.TextContent
//import io.modelcontextprotocol.kotlin.sdk.client.Client
//import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
//import kotlinx.coroutines.runBlocking
//import kotlinx.io.asSink
//import kotlinx.io.asSource
//import kotlinx.io.buffered
//import kotlinx.serialization.json.JsonObject
//import kotlin.jvm.optionals.getOrNull
//
//import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
////import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
//import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
//
//class MCPClient : AutoCloseable {
//
//    private val anthropic = AnthropicOkHttpClient.builder()
//        .apiKey(System.getenv("ANTHROPIC_API_KEY") )
//        .build()
//
//    // Initialize MCP client
//    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))
//
//    private val messageParamsBuilder: MessageCreateParams.Builder = MessageCreateParams.builder()
//        .model(Model.CLAUDE_3_5_SONNET_20241022)
//        .maxTokens(1024)
//
//    // List of tools offered by the server
//    private lateinit var tools: List<ToolUnion>
//    private var resourceContent: String? = null // Store resource content
//    private val resourceUri = "file:///C:/Users/developer/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/resources/who_guidelines_nutritional_interventions_anc.txt"
//    private fun JsonObject.toJsonValue(): JsonValue {
//        val mapper = ObjectMapper()
//        val node = mapper.readTree(this.toString())
//        return JsonValue.fromJsonNode(node)
//    }
//
//
////    private val resourceContents = mutableMapOf<String, String>()
//
//
//    // Connect to the server using the path to the server
//    suspend fun connectToServer(serverScriptPath: String) {
//            try {
//            // Build the command based on the file extension of the server script
//            val command = buildList {
//                when (serverScriptPath.substringAfterLast(".")) {
//                    "js" -> add("node")
//                    "py" -> add(if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3")
//                    "jar" -> addAll(listOf("java", "-jar"))
//                    else -> throw IllegalArgumentException("Server script must be a .js, .py or .jar file")
//                }
//                add(serverScriptPath)
//            }
//
//            // Start the server process
//            val process = ProcessBuilder(command).start()
//
//            // Setup I/O transport using the process streams
//            val transport = StdioClientTransport(
//                input = process.inputStream.asSource().buffered(),
//                output = process.outputStream.asSink().buffered()
//            )
//
//            // Connect the MCP client to the server using the transport
//            mcp.connect(transport)
//
//            // Request the list of available tools from the server
//            val toolsResult = mcp.listTools()
//            tools = toolsResult?.tools?.map { tool ->
//                ToolUnion.ofTool(
//                    Tool.builder()
//                        .name(tool.name)
//                        .description(tool.description ?: "")
//                        .inputSchema(
//                            Tool.InputSchema.builder()
//                                .type(JsonValue.from(tool.inputSchema.type))
//                                .properties(tool.inputSchema.properties.toJsonValue())
//                                .putAdditionalProperty("required", JsonValue.from(tool.inputSchema.required))
//                                .build()
//                        )
//                        .build()
//                )
//            } ?: emptyList()
//            println("Connected to server with tools: ${tools.joinToString(", ") { it.tool().get().name() }}")
//
//
//
////            val resourcesResult = mcp.listResources()
////                println("🔍 Available Resources:")
////                resourcesResult?.resources?.forEach {resource ->
////                    println("• ${resource.name} and uri = (${resource.uri})")
////
////                    if(resource.mimeType?.contains("text") == true){
////                        val readRequest = ReadResourceRequest(uri = resource.uri)
////                        val readResult = mcp.readResource(readRequest)
////                        val content = readResult?.contents
////                            ?.filterIsInstance<TextResourceContents>()
////                            ?.joinToString("\n"){it.text}
////
////                        if(!content.isNullOrBlank()){
////                            resourceContents[resource.name] = content
////                            println("Loaded resource '${resource.name}' (${content.length} characters)")
////                        }
////                        else{
////                            println("Couldnot read content of resource '${resource.name}'")
////                        }
////
////                    }
////                }
//
//
////                val resourcesResult = mcp.listResources()
////                println(resourcesResult)
////                println("🔍 Found ${resourcesResult?.resources?.size ?: 0} total resources:")
////                resourcesResult?.resources?.forEach { resource ->
////                    println("• ${resource.name} (URI: ${resource.uri}, MIME type: ${resource.mimeType})")
////
//////                     Add this to see which files are being filtered out by the text check
////                    if(resource.mimeType?.contains("text") != true) {
////                        println("  ⚠️ Skipping: Not detected as text content (MIME type: ${resource.mimeType})")
////                    }
////
////
//////                     Only process text resources
////                    if (resource.mimeType?.contains("text") == true) {
////                        val readRequest = ReadResourceRequest(uri = resource.uri)
////                        println("readRequest ${readRequest}")
////                        val readResult = mcp.readResource(readRequest)
//////                        println("readResult ${readResult}")
////
////                        val content = readResult?.contents
////                            ?.filterIsInstance<TextResourceContents>()
////                            ?.joinToString("\n") { it.text }
////
////                        println(content)
////
////                        if (!content.isNullOrBlank()) {
////                            resourceContents[resource.name] = content
////                            println("✔ Loaded resource '${resource.name}' (${content.length} characters)")
////                        } else {
////                            println("⚠ Could not read content of resource '${resource.name}'")
////                        }
////                    }
////
////
////                }
//
//
//                val resourcesResult = mcp.listResources()
//                val resource = resourcesResult?.resources?.find { it.uri == resourceUri }
//                println(resource?.uri )
//                println(resource?.mimeType )
//            if (resource != null) {
//                println("Found resource: ${resource.name} (${resource.uri})")
//                // Construct ReadResourceRequest
//                val readRequest = ReadResourceRequest(uri = resource.uri)
//                println("readRequest: ${readRequest}")
//                val readResult = mcp.readResource(readRequest)
//                println("readResult: ${readResult}")
//                this.resourceContent = readResult?.contents
//                    ?.filterIsInstance<TextResourceContents>()
//                    ?.joinToString("\n") { it.text }
//
//                println("Resource Text Content:\n$resourceContent")
////                resourceContent = readResult?.contents?.firstOrNull()?.text
//                if (resourceContent != null) {
//                    println("Successfully loaded resource content (${resourceContent?.length} characters)")
//                } else {
//                    println("Warning: Could not read resource content for ${resource.uri}")
//                }
//            } else {
//                println("Warning: Resource $resourceUri not found")
//            }
//        } catch (e: Exception) {
//            println("Failed to connect to MCP server: $e")
//            throw e
//        }
//    }
//
//
//
//
//    private val messages = mutableListOf<MessageParam>()
//
//    // Process a user query and return a string response
//    suspend fun processQuery(query: String): String {
//        // Create an initial message with a user's query
//
////        val messages = mutableListOf(
////            MessageParam.builder()
////                .role(MessageParam.Role.USER)
////                .content(query)
////                .build()
////        )
//
//        val context = resourceContent?.let {
//            // Truncate or summarize to avoid exceeding token limits
//            // For simplicity, take the first 5000 characters (adjust as needed)
//            val maxLength = 5000
//            if (it.length > maxLength) {
//                it.substring(0, maxLength) + "... [Content truncated]"
//            } else {
//                it
//            }
//        }
//
////        val combinedContext = resourceContents.entries.joinToString("\n\n") { (name, text) ->
////            val snippet = if (text.length > 2000) text.take(2000) + "... [truncated]" else text
////            "From $name:\nsnippet"
////        }
////
////        println("Context available:combinedContext != null}")
//        println("Context length: ${context?.length ?: 0} characters")
////
////        // Combine context and user query
//        val fullQuery = if (context != null) {
//            """
//            Context from WHO Nutritional Interventions Guidelines:
//            $context
//
//            User Query:
//            $query
//            """.trimIndent()
//        } else {
//            query
//        }
//
////        val fullQuery = if (combinedContext.isNotBlank()) {
////            """
////            Context from WHO Guidelines Resources:
////            $combinedContext
////
////            User Query:
////            $query
////            """.trimIndent()
////        } else {
////            query
////        }
//
//
//
//        messages.add(
//            MessageParam.builder()
//                .role(MessageParam.Role.USER)
//                .content(fullQuery)
//                .build()
//        )
//
//        // Send the query to the Anthropic model and get the response
//        val response = anthropic.messages().create(
//            messageParamsBuilder
//                .messages(messages)
//                .tools(tools)
//                .build()
//        )
//
//        val finalText = mutableListOf<String>()
//
//        response.content().forEach { content ->
//            when {
//                // Append text outputs from the response
//                content.isText() -> {
//                    val text = content.text().getOrNull()?.text()
//                    if (!text.isNullOrBlank()) {
//                        finalText.add(text)
//
//                        // Save assistant response to memory
//                        messages.add(
//                            MessageParam.builder()
//                                .role(MessageParam.Role.ASSISTANT)
//                                .content(text)
//                                .build()
//                        )
//                    }
//                }
//
//                // If the response indicates a tool use, process it further
//                content.isToolUse() -> {
//                    val toolName = content.toolUse().get().name()
//                    val toolArgs =
//                        content.toolUse().get()._input().convert(object : TypeReference<Map<String, JsonValue>>() {})
//
//                    // Call the tool with provided arguments
//                    val result = mcp.callTool(
//                        name = toolName,
//                        arguments = toolArgs ?: emptyMap()
//                    )
//
//                    finalText.add("[Calling tool $toolName with args $toolArgs]")
//
//                    // Add the tool_result to messages
//                    val toolResultContent = """
//                    {
//                      "type": "tool_result",
//                      "tool_name": "$toolName",
//                      "result": "${result?.content?.joinToString("\n") { (it as TextContent).text ?: "" }}"
//                    }
//                    """.trimIndent()
//
//                    messages.add(
//                        MessageParam.builder()
//                            .role(MessageParam.Role.USER)
//                            .content(toolResultContent)
//                            .build()
//                    )
//
//                    // Retrieve an updated response after tool execution
//                    val aiResponse = anthropic.messages().create(
//                        messageParamsBuilder
//                            .messages(messages)
//                            .build()
//                    )
//
//                    val aiReply = aiResponse.content().firstOrNull()?.text()?.getOrNull()?.text()
//                    if (!aiReply.isNullOrBlank()) {
//                        finalText.add(aiReply)
//
//                        // Save assistant's new response after tool use
//                        messages.add(
//                            MessageParam.builder()
//                                .role(MessageParam.Role.ASSISTANT)
//                                .content(aiReply)
//                                .build()
//                        )
//                    }
//                }
//            }
//        }
//
//        println(messages)
//
//        return finalText.joinToString("\n", prefix = "", postfix = "")
//    }
//
//
//
//    // Main chat loop for interacting with the user
//    suspend fun chatLoop() {
//        println("\n==============================================")
//        println("\nMCP Client Started!")
//        println("Type your queries or 'quit' to exit.")
//        println("\n==============================================")
//
//        while (true) {
//            print("\nQuery: ")
//            val message = readLine() ?: break
//            if (message.lowercase() == "quit") break
//            val response = processQuery(message)
//            println("\n$response")
//        }
//    }
//
//    override fun close() {
//        runBlocking {
//            mcp.close()
//            anthropic.close()
//        }
//    }
//}
//
////
////package io.modelcontextprotocol.sample.client
////
////import com.anthropic.client.okhttp.AnthropicOkHttpClient
////import com.anthropic.core.JsonValue
////import com.anthropic.models.messages.*
////import com.fasterxml.jackson.core.type.TypeReference
////import com.fasterxml.jackson.databind.ObjectMapper
////import io.modelcontextprotocol.kotlin.sdk.Implementation
////import io.modelcontextprotocol.kotlin.sdk.TextContent
////import io.modelcontextprotocol.kotlin.sdk.client.Client
////import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
////import kotlinx.coroutines.runBlocking
////import kotlinx.io.asSink
////import kotlinx.io.asSource
////import kotlinx.io.buffered
////import kotlinx.serialization.json.JsonObject
////import kotlin.jvm.optionals.getOrNull
////
////import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
//////import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
////import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
////
////class MCPClient : AutoCloseable {
////    // Configures using the `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` environment variables
//////    private val anthropic = AnthropicOkHttpClient.fromEnv()
////////        .apiKey(System.getenv("ANTHROPIC_API_KEY") ?: "your_api_key_here")
////////        .build()
////
////    private val anthropic = AnthropicOkHttpClient.builder()
////        .apiKey(System.getenv("ANTHROPIC_API_KEY") )
////        .build()
////
////    // Initialize MCP client
////    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))
////
////    private val messageParamsBuilder: MessageCreateParams.Builder = MessageCreateParams.builder()
////        .model(Model.CLAUDE_3_5_SONNET_20241022)
////        .maxTokens(1024)
////
////    // List of tools offered by the server
////    private lateinit var tools: List<ToolUnion>
////    private var resourceContent: String? = null // Store resource content
////    //    private val resourceUri = "file:///C://Users//developer//Downloads//kotlin-sdk//samples//weather-stdio-server//src//main//kotlin//io//modelcontextprotocol//sample//server//resources//who_guidelines_nutritional_interventions_anc.txt "
//////    private val resourceUri = "file:///C:/Users/developer/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/resources/who_guidelines_nutritional_interventions_anc.txt"
////    private fun JsonObject.toJsonValue(): JsonValue {
////        val mapper = ObjectMapper()
////        val node = mapper.readTree(this.toString())
////        return JsonValue.fromJsonNode(node)
////    }
////
////
////    private val resourceContents = mutableMapOf<String, String>()
////
////
////    // Connect to the server using the path to the server
////    suspend fun connectToServer(serverScriptPath: String) {
////        try {
////            // Build the command based on the file extension of the server script
////            val command = buildList {
////                when (serverScriptPath.substringAfterLast(".")) {
////                    "js" -> add("node")
////                    "py" -> add(if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3")
////                    "jar" -> addAll(listOf("java", "-jar"))
////                    else -> throw IllegalArgumentException("Server script must be a .js, .py or .jar file")
////                }
////                add(serverScriptPath)
////            }
////
////            // Start the server process
////            val process = ProcessBuilder(command).start()
////
////            // Setup I/O transport using the process streams
////            val transport = StdioClientTransport(
////                input = process.inputStream.asSource().buffered(),
////                output = process.outputStream.asSink().buffered()
////            )
////
////            // Connect the MCP client to the server using the transport
////            mcp.connect(transport)
////
////            // Request the list of available tools from the server
////            val toolsResult = mcp.listTools()
////            tools = toolsResult?.tools?.map { tool ->
////                ToolUnion.ofTool(
////                    Tool.builder()
////                        .name(tool.name)
////                        .description(tool.description ?: "")
////                        .inputSchema(
////                            Tool.InputSchema.builder()
////                                .type(JsonValue.from(tool.inputSchema.type))
////                                .properties(tool.inputSchema.properties.toJsonValue())
////                                .putAdditionalProperty("required", JsonValue.from(tool.inputSchema.required))
////                                .build()
////                        )
////                        .build()
////                )
////            } ?: emptyList()
////            println("Connected to server with tools: ${tools.joinToString(", ") { it.tool().get().name() }}")
////
////
////            val resourcesResult = mcp.listResources()
////            println(resourcesResult)
////            println("🔍 Found ${resourcesResult?.resources?.size ?: 0} total resources:")
////            resourcesResult?.resources?.forEach { resource ->
////                println("• ${resource.name} (URI: ${resource.uri}, MIME type: ${resource.mimeType})")
////
////
//////                     Add this to see which files are being filtered out by the text check
////                if(resource.mimeType?.contains("text") == true) {
////                    println("  ⚠️ Skipping: Not detected as text content (MIME type: ${resource.mimeType})")
////                }
////
////
//////                     Only process text resources
////
//////                /                if (resource.mimeType?.contains("text") == true) {
//////                    val readRequest = ReadResourceRequest(uri = resource.uri)
//////                    println("readRequest ${readRequest}")
//////
//////                    val readResult = mcp.readResource(readRequest)
////////                    return readResult?.contents
//////                   println("readResult ${readResult}")
//////                    if (readResult == null) {
//////                        println("❌ readResource returned null for ${resource.uri}")
//////                    } else {
//////                        println("✅ readResource success: $readResult")
//////                    }
//////
//////                    val content = readResult?.contents
//////                        ?.filterIsInstance<TextResourceContents>()
//////                        ?.joinToString("\n") { it.text }
//////
//////                    println(content)
//////
//////                    if (!content.isNullOrBlank()) {
//////                        resourceContents[resource.name] = content
//////                        println("✔ Loaded resource '${resource.name}' (${content.length} characters)")
//////                    } else {
//////                        println("⚠ Could not read content of resource '${resource.name}'")
//////                    }
////
////                if (resource != null) {
////                    println("Found resource: ${resource.name} (${resource.uri})")
////                    // Construct ReadResourceRequest
////                    val readRequest = ReadResourceRequest(uri = resource.uri)
////                    println("readRequest: ${readRequest}")
////                    println("Before calling readResource()")
////                    val readResult = mcp.readResource(readRequest)
////                    println("After calling readResource()")
////                    println("readResult: ${readResult}")
////                    this.resourceContent = readResult?.contents
////                        ?.filterIsInstance<TextResourceContents>()
////                        ?.joinToString("\n") { it.text }
////
////        //                println("Resource Text Content:\n$resourceContent")
////        //                resourceContent = readResult?.contents?.firstOrNull()?.text
////                    if (resourceContent != null) {
////                        println("Successfully loaded resource content (${resourceContent?.length} characters)")
////                    } else {
////                        println("Warning: Could not read resource content for ${resource.uri}")
////                    }
////            } else {
////                println("Warning: Resource  not found")
////            }
////            }
////
////
////        } catch (e: Exception) {
////            println("Failed to connect to MCP server: $e")
////            throw e
////        }
////    }
////
////
////
////
////    private val messages = mutableListOf<MessageParam>()
////
////    // Process a user query and return a string response
////    suspend fun processQuery(query: String): String {
////        // Create an initial message with a user's query
////
////
////
////        val combinedContext = resourceContents.entries.joinToString("\n\n") { (name, text) ->
////            val snippet = if (text.length > 2000) text.take(2000) + "... [truncated]" else text
////            "From $name:$snippet"
////        }
////
//////        println("Context available:combinedContext != null}")
////        println("Context available: ${combinedContext.isNotBlank()}")
////        println("Context length: ${combinedContext?.length ?: 0} characters")
////
////
////        val fullQuery = if (combinedContext.isNotBlank()) {
////            """
////            Context from WHO Guidelines Resources:
////            $combinedContext
////
////            User Query:
////            $query
////            """.trimIndent()
////        } else {
////            query
////        }
////
////
////
////        messages.add(
////            MessageParam.builder()
////                .role(MessageParam.Role.USER)
////                .content(fullQuery)
////                .build()
////        )
////
////        // Send the query to the Anthropic model and get the response
////        val response = anthropic.messages().create(
////            messageParamsBuilder
////                .messages(messages)
////                .tools(tools)
////                .build()
////        )
////
////        val finalText = mutableListOf<String>()
////
////        response.content().forEach { content ->
////            when {
////                // Append text outputs from the response
////                content.isText() -> {
////                    val text = content.text().getOrNull()?.text()
////                    if (!text.isNullOrBlank()) {
////                        finalText.add(text)
////
////                        // Save assistant response to memory
////                        messages.add(
////                            MessageParam.builder()
////                                .role(MessageParam.Role.ASSISTANT)
////                                .content(text)
////                                .build()
////                        )
////                    }
////                }
////
////                // If the response indicates a tool use, process it further
////                content.isToolUse() -> {
////                    val toolName = content.toolUse().get().name()
////                    val toolArgs =
////                        content.toolUse().get()._input().convert(object : TypeReference<Map<String, JsonValue>>() {})
////
////                    // Call the tool with provided arguments
////                    val result = mcp.callTool(
////                        name = toolName,
////                        arguments = toolArgs ?: emptyMap()
////                    )
////
////                    finalText.add("[Calling tool $toolName with args $toolArgs]")
////
////                    // Add the tool_result to messages
////                    val toolResultContent = """
////            {
////              "type": "tool_result",
////              "tool_name": "$toolName",
////              "result": "${result?.content?.joinToString("\n") { (it as TextContent).text ?: "" }}"
////            }
////            """.trimIndent()
////
////                    messages.add(
////                        MessageParam.builder()
////                            .role(MessageParam.Role.USER)
////                            .content(toolResultContent)
////                            .build()
////                    )
////
////                    // Retrieve an updated response after tool execution
////                    val aiResponse = anthropic.messages().create(
////                        messageParamsBuilder
////                            .messages(messages)
////                            .build()
////                    )
////
////                    val aiReply = aiResponse.content().firstOrNull()?.text()?.getOrNull()?.text()
////                    if (!aiReply.isNullOrBlank()) {
////                        finalText.add(aiReply)
////
////                        // Save assistant's new response after tool use
////                        messages.add(
////                            MessageParam.builder()
////                                .role(MessageParam.Role.ASSISTANT)
////                                .content(aiReply)
////                                .build()
////                        )
////                    }
////                }
////            }
////        }
////
////        println(messages)
////
////        return finalText.joinToString("\n", prefix = "", postfix = "")
////    }
////
////
////
////    // Main chat loop for interacting with the user
////    suspend fun chatLoop() {
////        println("\n==============================================")
////        println("\nMCP Client Started!")
////        println("Type your queries or 'quit' to exit.")
////        println("\n==============================================")
////
////        while (true) {
////            print("\nQuery: ")
////            val message = readLine() ?: break
////            if (message.lowercase() == "quit") break
////            val response = processQuery(message)
////            println("\n$response")
////        }
////    }
////
////    override fun close() {
////        runBlocking {
////            mcp.close()
////            anthropic.close()
////        }
////    }
////}