# Kotlin MCP Weather STDIO Server

This project demonstrates how to build a Model Context Protocol (MCP) server in Kotlin that provides weather-related
tools by consuming the National Weather Service (weather.gov) API. The server uses STDIO as the transport layer and
leverages the Kotlin MCP SDK to expose weather forecast and alert tools.

For more information about the MCP SDK and protocol, please refer to
the [MCP documentation](https://modelcontextprotocol.io/introduction).

## Prerequisites

- Java 17 or later
- Gradle (or the Gradle wrapper provided with the project)
- Basic understanding of MCP concepts
- Basic understanding of Kotlin and Kotlin ecosystems (sush as kotlinx-serialization, coroutines, ktor)

## MCP Weather Server

The project provides:

- A lightweight MCP server built with Kotlin.
- STDIO transport layer implementation for server-client communication.
- Two weather tools:
    - **Weather Forecast Tool** — returns details such as temperature, wind information, and a detailed forecast for a
      given latitude/longitude.
    - **Weather Alerts Tool** — returns active weather alerts for a given US state.

## Building and running

Use the Gradle wrapper to build the application. In a terminal run:

```shell
./gradlew clean build -x test
```

To run the server:

```shell
java -jar build/libs/<your-jar-name>.jar
```

> [!NOTE]
> The server uses STDIO transport, so it is typically launched in an environment where the client connects via standard
> input/output.

## Tool Implementation

The project provides two different approaches to register MCP tools using the Kotlin MCP SDK:

### Traditional Approach

The traditional approach uses the `addTool` method to register tools with explicit schema definitions.

#### 1. Weather Forecast Tool

```kotlin
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
    // Implementation tool
}
```

#### 2. Weather Alerts Tool

```kotlin
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
    // Implementation tool
}
```

### Annotation-Based Approach

The project also demonstrates an alternative, more idiomatic approach using Kotlin annotations. This approach simplifies tool definition by leveraging Kotlin's type system and reflection.

To use the annotation-based approach, run the server with:
```shell
java -jar build/libs/<your-jar-name>.jar --use-annotations
```

#### Tool implementation with annotations:

```kotlin
class WeatherToolsAnnotated(private val httpClient: HttpClient) {
    
    @McpTool(
        name = "get_alerts",
        description = "Get weather alerts for a US state"
    )
    suspend fun getAlerts(
        @McpParam(
            description = "Two-letter US state code (e.g. CA, NY)",
            type = "string"
        ) state: String
    ): CallToolResult {
        // Implementation
    }

    @McpTool(
        name = "get_forecast",
        description = "Get weather forecast for a specific latitude/longitude"
    )
    suspend fun getForecast(
        @McpParam(description = "The latitude coordinate") latitude: Double,
        @McpParam(description = "The longitude coordinate") longitude: Double
    ): CallToolResult {
        // Implementation
    }
}
```

Then register the tools using:

```kotlin
val weatherTools = WeatherToolsAnnotated(httpClient)
server.registerAnnotatedTools(weatherTools)
```

This approach provides several benefits:
- More idiomatic Kotlin code
- Parameter types are automatically inferred from Kotlin's type system
- Reduced boilerplate for tool registration
- Better IDE support with autocompletion and compile-time checking

## Client Integration

### Kotlin Client Example

Since the server uses STDIO for transport, the client typically connects via standard input/output streams. A sample
client implementation can be found in the tests, demonstrating how to send tool requests and process responses.

### Claude for Desktop

To integrate with Claude Desktop, add the following configuration to your Claude Desktop settings:

```json
{
  "mcpServers": {
    "weather": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/<your-jar-name>.jar"
      ]
    }
  }
}
```

> [!NOTE]
> Replace `/absolute/path/to/<your-jar-name>.jar` with the actual absolute path to your built jar file.

## Additional Resources

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Ktor Client Documentation](https://ktor.io/docs/welcome.html)
- [Kotlinx Serialization](https://kotlinlang.org/docs/serialization.html)

