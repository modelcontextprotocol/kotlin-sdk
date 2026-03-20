package io.modelcontextprotocol.sample.client

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUnion
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit

private const val MODEL = "claude-sonnet-4-20250514"
private const val MAX_TOKENS = 1024L

class MCPClient(apiKey: String) : AutoCloseable {
    private val anthropic = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    // Initialize MCP client
    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))

    // Server process reference for cleanup
    private var serverProcess: Process? = null

    // List of tools offered by the server
    private lateinit var tools: List<ToolUnion>

    private fun JsonObject.toJsonValue(): JsonValue {
        val mapper = ObjectMapper()
        val node = mapper.readTree(this.toString())
        return JsonValue.fromJsonNode(node)
    }

    // Connect to the server using the path to the server
    suspend fun connectToServer(serverScriptPath: String) {
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
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        }
        serverProcess = process

        // Setup I/O transport using the process streams
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
        )

        // Connect the MCP client to the server using the transport
        mcp.connect(transport)

        // Request the list of available tools from the server
        val toolsResult = mcp.listTools()
        tools = toolsResult.tools.map { tool ->
            ToolUnion.ofTool(
                Tool.builder()
                    .name(tool.name)
                    .description(tool.description ?: "")
                    .inputSchema(
                        Tool.InputSchema.builder()
                            .type(JsonValue.from(tool.inputSchema.type))
                            .properties(tool.inputSchema.properties?.toJsonValue() ?: EmptyJsonObject.toJsonValue())
                            .putAdditionalProperty("required", JsonValue.from(tool.inputSchema.required))
                            .build(),
                    )
                    .build(),
            )
        }
        println("Connected to server with tools: ${tools.joinToString(", ") { it.tool().get().name() }}")
    }

    // Process a user query and return a string response
    suspend fun processQuery(query: String): String {
        // Create an initial message with a user's query
        val messages = mutableListOf(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(query)
                .build(),
        )

        // Send the query to the Anthropic model and get the response
        val response = anthropic.messages().create(
            MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .messages(messages)
                .tools(tools)
                .build(),
        )

        val finalText = mutableListOf<String>()
        val toolResults = mutableListOf<ContentBlockParam>()

        response.content().forEach { content ->
            when {
                // Append text outputs from the response
                content.isText() -> finalText.add(content.text().get().text())

                // If the response indicates a tool use, process it further
                content.isToolUse() -> {
                    val toolUse = content.toolUse().get()
                    val toolName = toolUse.name()
                    val toolUseId = toolUse.id()
                    val toolArgs =
                        toolUse._input().convert(object : TypeReference<Map<String, JsonValue>>() {})

                    // Call the tool with provided arguments
                    val result = mcp.callTool(
                        name = toolName,
                        arguments = toolArgs ?: emptyMap(),
                    )
                    finalText.add("[Calling tool $toolName with args $toolArgs]")

                    // Build tool_result content block with tool_use_id
                    val toolResultContent = result.content
                        .filterIsInstance<TextContent>()
                        .joinToString("\n") { it.text }

                    val toolResultBlock = ToolResultBlockParam.builder()
                        .toolUseId(toolUseId)
                        .content(toolResultContent)
                        .apply { if (result.isError == true) isError(true) }
                        .build()

                    toolResults.add(ContentBlockParam.ofToolResult(toolResultBlock))
                }
            }
        }

        // If there were tool calls, send tool results back and get final response
        if (toolResults.isNotEmpty()) {
            // Add the full assistant response (includes tool_use blocks)
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(response.content().map { it.toParam() })
                    .build(),
            )

            // Add user message with tool results
            messages.add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build(),
            )

            // Retrieve an updated response after tool execution (without tools)
            val aiResponse = anthropic.messages().create(
                MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(MAX_TOKENS)
                    .messages(messages)
                    .build(),
            )

            // Append the updated response to final text
            aiResponse.content()
                .filter { it.isText() }
                .forEach { finalText.add(it.text().get().text()) }
        }

        return finalText.joinToString("\n", prefix = "", postfix = "")
    }

    // Main chat loop for interacting with the user
    suspend fun chatLoop() {
        println("\nMCP Client Started!")
        println("Type your queries or 'quit' to exit.")

        while (true) {
            print("\nQuery: ")
            val message = readlnOrNull() ?: break
            if (message.trim().lowercase() == "quit") break

            try {
                val response = processQuery(message)
                println("\n$response")
            } catch (e: Exception) {
                println("\nError: ${e.message}")
            }
        }
    }

    override fun close() {
        runBlocking {
            mcp.close()
        }
        serverProcess?.let { process ->
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
        anthropic.close()
    }
}
