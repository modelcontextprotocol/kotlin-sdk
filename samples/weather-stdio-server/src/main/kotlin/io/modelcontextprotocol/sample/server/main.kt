package io.modelcontextprotocol.sample.server

fun main(args: Array<String>) {
    val useAnnotations = args.contains("--use-annotations")
    
    if (useAnnotations) {
        println("Starting annotated MCP Weather server...")
        `run annotated mcp server`()
    } else {
        println("Starting traditional MCP Weather server...")
        `run mcp server`()
    }
}