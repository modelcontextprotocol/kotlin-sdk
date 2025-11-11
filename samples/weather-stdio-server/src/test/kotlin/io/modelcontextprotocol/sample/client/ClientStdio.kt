package io.modelcontextprotocol.sample.client

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun main(): Unit = runBlocking {
    val process = ProcessBuilder("java", "-jar", "./build/libs/weather-stdio-server-0.1.0-all.jar")
        .start()

    val transport = StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered(),
    )

    // Initialize the MCP client with client information
    val client = Client(
        clientInfo = Implementation(name = "weather", version = "1.0.0"),
    )

    client.connect(transport)

    val toolsList = client.listTools().tools.map { it.name }
    println("Available Tools = $toolsList")

    val weatherForecastResult = client.callTool(
        CallToolRequest(
            name = "get_forecast",
            arguments = buildJsonObject {
                put("latitude", JsonPrimitive(38.5816))
                put("longitude", JsonPrimitive(-121.4944))
            },
        ),
    )?.content?.map { if (it is TextContent) it.text else it.toString() }

    println("Weather Forcast: ${weatherForecastResult?.joinToString(separator = "\n", prefix = "\n", postfix = "\n")}")

    val alertResult =
        client.callTool(
            CallToolRequest(
                name = "get_alerts",
                arguments = JsonObject(mapOf("state" to JsonPrimitive("TX"))),
            ),
        )?.content?.map { if (it is TextContent) it.text else it.toString() }

    println("Alert Response = $alertResult")

    client.close()
}
