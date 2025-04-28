package io.modelcontextprotocol.sample.server

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*

import java.nio.file.Files
import java.nio.file.Paths

//import java.io.File

// Main function to run the MCP server
fun `run mcp server`() {
    // Base URL for the Weather API
    val baseUrl = "https://api.weather.gov"

    // Create an HTTP client with a default request configuration and JSON content negotiation
    val httpClient = HttpClient {
        defaultRequest {
            url(baseUrl)
            headers {
                append("Accept", "application/geo+json")
                append("User-Agent", "WeatherApiClient/1.0")
            }
            contentType(ContentType.Application.Json)
        }
        // Install content negotiation plugin for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    // Create the MCP Server instance with a basic implementation
    val server = Server(
        Implementation(
            name = "weather", // Tool name is "weather"
            version = "1.0.0" // Version of the implementation
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources( subscribe = true,listChanged = true)
            )
        )
    )

    // Register a tool to fetch weather alerts by state
    server.addTool(
        name = "get_alerts",
        description = """
            Get weather alerts for a US state. Input is Two-letter US state code (e.g. CA, NY)
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("state") {
                    put("type", "string")
                    put("description", "Two-letter US state code (e.g. CA, NY)")
                }
            },
            required = listOf("state")
        )
    ) { request ->
        val state = request.arguments["state"]?.jsonPrimitive?.content
        if (state == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'state' parameter is required."))
            )
        }

        val alerts = httpClient.getAlerts(state)

        CallToolResult(content = alerts.map { TextContent(it) })
    }

    // Register a tool to fetch weather forecast by latitude and longitude
    server.addTool(
        name = "get_forecast",
        description = """
            Get weather forecast for a specific latitude/longitude
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("latitude") {
                    put("type", "number")
                }
                putJsonObject("longitude") {
                    put("type", "number")
                }
            },
            required = listOf("latitude", "longitude")
        )
    ) { request ->
        val latitude = request.arguments["latitude"]?.jsonPrimitive?.doubleOrNull
        val longitude = request.arguments["longitude"]?.jsonPrimitive?.doubleOrNull
        if (latitude == null || longitude == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'latitude' and 'longitude' parameters are required."))
            )
        }

        val forecast = httpClient.getForecast(latitude, longitude)

        CallToolResult(content = forecast.map { TextContent(it) })
    }

    val files = listOf(
//        Triple("file:///C:/Users/tanu0/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/context-files/Iron_folic_acid_supplements_who_guidelines.txt", "Iron folic acid supplement by who for pregnant women", "C:\\Users\\tanu0\\Downloads\\kotlin-sdk\\samples\\weather-stdio-server\\src\\main\\kotlin\\io\\modelcontextprotocol\\sample\\server\\context-files\\Iron_folic_acid_supplements_who_guidelines.txt"),
//        Triple("file:///C:/Users/tanu0/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/context-files/nutritional_intervention_who_guidelines.txt", "nutritional intervention guidelines by WHO", "C:\\Users\\tanu0\\Downloads\\kotlin-sdk\\samples\\weather-stdio-server\\src\\main\\kotlin\\io\\modelcontextprotocol\\sample\\server\\context-files\\nutritional_intervention_who_guidelines.txt"),
        Triple("file:///C:/Users/developer/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/resources/who_ANC_Guideline_Calcium_supplements.txt", "who ANC Guideline Calcium supplements", "C:\\Users\\developer\\Downloads\\kotlin-sdk\\samples\\weather-stdio-server\\src\\main\\kotlin\\io\\modelcontextprotocol\\sample\\server\\resources\\who_ANC_Guideline_Calcium_supplements.txt"),
        Triple("file:///C:/Users/developer/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/resources/who_ANC_Guideline_Zinc_supplements.txt", "who ANC Guideline Zinc supplements", "C:\\Users\\developer\\Downloads\\kotlin-sdk\\samples\\weather-stdio-server\\src\\main\\kotlin\\io\\modelcontextprotocol\\sample\\server\\resources\\who_ANC_Guideline_Zinc_supplements.txt"),
        Triple("file:///C:/Users/developer/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/resources/who_ANC_Guideline_Vitamin_A_Supplements.txt", "who ANC Guideline Vitamin A Supplements", "C:\\Users\\developer\\Downloads\\kotlin-sdk\\samples\\weather-stdio-server\\src\\main\\kotlin\\io\\modelcontextprotocol\\sample\\server\\resources\\who_ANC_Guideline_Vitamin_A_Supplements.txt"),
        Triple("file:///C:/Users/developer/Downloads/kotlin-sdk/samples/weather-stdio-server/src/main/kotlin/io/modelcontextprotocol/sample/server/resources/who_guidelines_nutritional_interventions_anc.txt", "who guidelines nutritional interventions anc", "C:\\Users\\developer\\Downloads\\kotlin-sdk\\samples\\weather-stdio-server\\src\\main\\kotlin\\io\\modelcontextprotocol\\sample\\server\\resources\\who_guidelines_nutritional_interventions_anc.txt")
    )

    for ((uri, name, path) in files) {
        server.addResource(
            uri = uri,
            name = name,
            description = "Resource for $name",
            mimeType = "text/plain"
        ) { request ->
            val content = Files.readString(Paths.get(path)) // 🔥 Read from file at runtime
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = content,
                        uri = request.uri,
                        mimeType = "text/plain"
                    )
                )
            )
        }
    }

    // Create a transport using standard IO for server communication
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )

    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}
