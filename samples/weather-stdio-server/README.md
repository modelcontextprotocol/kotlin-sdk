# Weather STDIO Server

A minimal MCP server that exposes weather forecast and alert tools using the National Weather
Service API over STDIO transport.

## Overview

This sample shows how to build a STDIO-based MCP server in Kotlin. It registers two tools that
query the [weather.gov](https://www.weather.gov/) API — one for weather forecasts by
latitude/longitude and one for active alerts by US state. Because it uses STDIO, the server is
launched as a subprocess by an MCP client or a desktop application like Claude Desktop.

## Prerequisites

- JDK 17+
- Internet access (the server calls the weather.gov API at runtime)

## Build & Run

Run the server (it communicates via stdin/stdout):

```shell
./gradlew :samples:weather-stdio-server:run
```

### MCP Inspector

Build the fat JAR first, then connect with the
[MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector):

```shell
./gradlew :samples:weather-stdio-server:build
npx @modelcontextprotocol/inspector -- java -jar samples/weather-stdio-server/build/libs/weather-stdio-server-0.1.0-all.jar
```

### Claude Desktop integration

Add the following to your Claude Desktop configuration:

```json
{
    "mcpServers": {
        "weather": {
            "command": "java",
            "args": [
                "-jar",
                "/absolute/path/to/samples/weather-stdio-server/build/libs/weather-stdio-server-0.1.0-all.jar"
            ]
        }
    }
}
```

## MCP Capabilities

### Tools

| Name           | Description                                                                              |
|----------------|------------------------------------------------------------------------------------------|
| `get_forecast` | Returns weather forecast for a given `latitude` / `longitude` using the weather.gov API. |
| `get_alerts`   | Returns active weather alerts for a two-letter US `state` code (e.g. `CA`, `NY`).        |

## Additional Resources

- [MCP Specification](https://modelcontextprotocol.io/specification/latest)
- [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [National Weather Service API](https://www.weather.gov/documentation/services-web-api)
