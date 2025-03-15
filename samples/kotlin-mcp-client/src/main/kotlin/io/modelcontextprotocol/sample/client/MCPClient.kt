package io.modelcontextprotocol.sample.client

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.MessageCreateParams
import com.anthropic.models.MessageParam
import com.anthropic.models.Tool
import com.anthropic.models.ToolUnion
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.jvm.optionals.getOrNull

class MCPClient : AutoCloseable {
    // Configures using the `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` environment variables
    private val anthropic = AnthropicOkHttpClient.fromEnv()

    // Initialize MCP client
    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-client-cli", version = "1.0.0"))

    private val messageParamsBuilder: MessageCreateParams.Builder = MessageCreateParams.builder()
        .model("claude-3-5-sonnet-20241022")
        .maxTokens(1000)

    // List of tools offered by the server
    private lateinit var tools: List<ToolUnion>

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
                                .properties(JsonValue.from(tool.inputSchema.properties))
                                .putAdditionalProperty(
                                    "required",
                                    JsonValue.from(tool.inputSchema.required ?: emptyList<String>())
                                )
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

    // Process a user query and return a string response
    suspend fun processQuery(query: String): String {
        // Create an initial message with a user's query
        val messages = mutableListOf(
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

        val finalText = mutableListOf<String>()
        response.content().forEach { content ->
            when {
                // Append text outputs from the response
                content.isText() -> finalText.add(content.text().getOrNull()?.text() ?: "")

                // If the response indicates a tool use, process it further
                content.isToolUse() -> {
                    val toolName = content.toolUse().get().name()
                    val toolArgs = content.toolUse().get()._additionalProperties()

                    // Call the tool with provided arguments
                    val result = mcp.callTool(
                        name = toolName,
                        arguments = toolArgs
                    )
                    finalText.add("[Calling tool $toolName with args $toolArgs]")

                    // Add the tool result message to the conversation
                    messages.add(
                        MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(result?.content.toString())
                            .build()
                    )

                    // Retrieve an updated response after tool execution
                    val aiResponse = anthropic.messages().create(
                        messageParamsBuilder
                            .messages(messages)
                            .build()
                    )

                    // Append the updated response to final text
                    finalText.add(aiResponse.content().first().text().getOrNull()?.text() ?: "")
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