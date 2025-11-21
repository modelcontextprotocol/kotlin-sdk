package io.modelcontextprotocol.sample.client

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

fun main(): Unit = runBlocking {
    val process = ProcessBuilder(
        "java",
        "-jar",
        "${System.getProperty("user.dir")}/build/libs/weather-stdio-server-0.1.0-all.jar",
    ).redirectErrorStream(true)
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
        name = "get_forecast",
        arguments = mapOf(
            "latitude" to 38.5816,
            "longitude" to -121.4944,
        ),
    ).content.map { if (it is TextContent) it.text else it.toString() }

    println("Weather Forecast: ${weatherForecastResult.joinToString(separator = "\n", prefix = "\n", postfix = "\n")}")

    val alertResult =
        client.callTool(
            name = "get_alert",
            arguments = mapOf("state" to "TX"),
        ).content.map { if (it is TextContent) it.text else it.toString() }

    println("Alert Response = $alertResult")

    client.close()
}
