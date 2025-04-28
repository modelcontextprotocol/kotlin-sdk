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

import java.nio.file.Files
import java.nio.file.Paths

import java.io.File
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents

class MCPClient : AutoCloseable {

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



    private val resourceContents: MutableMap<String, String> = mutableMapOf()

    suspend fun connectToServer(serverScriptPath: String) {
        try {
            // Build command to start server
            val command = buildList {
                when (serverScriptPath.substringAfterLast(".")) {
                    "js" -> add("node")
                    "py" -> add(if (System.getProperty("os.name").lowercase().contains("win")) "python" else "python3")
                    "jar" -> addAll(listOf("java", "-jar"))
                    else -> throw IllegalArgumentException("Server script must be a .js, .py or .jar file")
                }
                add(serverScriptPath)
            }

            // Start server process
            val process = ProcessBuilder(command).start()

            // Setup transport
            val transport = StdioClientTransport(
                input = process.inputStream.asSource().buffered(),
                output = process.outputStream.asSink().buffered()
            )

            // Connect MCP client
            mcp.connect(transport)

            // List tools
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

//            // List all resources
            val resourcesResult = mcp.listResources()
            val resources = resourcesResult?.resources ?: emptyList()

            println("Found ${resources.size} resources from server.")

            for (resource in resources) {
                println("Loading resource: ${resource.name} (${resource.uri})")
                val readRequest = ReadResourceRequest(uri = resource.uri)
                val readResult = mcp.readResource(readRequest)

                val content = readResult?.contents
                    ?.filterIsInstance<TextResourceContents>()
                    ?.joinToString("\n") { it.text }
                if (content != null) {
                    resourceContents[resource.uri] = content
                    println("Successfully loaded resource (${content.length} characters) for URI: ${resource.uri}")
                } else {
                    println("Warning: No content found for resource: ${resource.uri}")
                }
            }

        } catch (e: Exception) {
            println("Failed to connect to MCP server: $e")
            throw e
        }
    }

    private val messages = mutableListOf<MessageParam>()

    // Process a user query and return a string response
    suspend fun processQuery(query: String): String {
        // Create an initial message with a user's query

//        / Prepend resources as SYSTEM message if they are loaded
        if (resourceContents.isNotEmpty()) {
            val combinedResourceText = resourceContents.values.joinToString("\n\n")
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)  // system role for context
                    .content("Reference Resources:\n$combinedResourceText")
                    .build()
            )
        }


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
