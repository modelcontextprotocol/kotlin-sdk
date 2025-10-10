@file:Suppress("ktlint:standard:no-empty-class-body", "ktlint:standard:kdoc")
/**
 * # MCP Kotlin SDK
 *
 * A Kotlin Multiplatform implementation of the Model Context Protocol (MCP).
 *
 * This is the main SDK module that provides a convenient single dependency
 * for all MCP functionality including:
 *
 * - Core protocol types and utilities ([kotlin-sdk-core])
 * - Client implementations ([kotlin-sdk-client])
 * - Server implementations ([kotlin-sdk-server])
 *
 * ## Usage
 *
 * Add this dependency to your project to get access to all MCP Kotlin SDK functionality:
 *
 * ```kotlin
 * implementation("io.modelcontextprotocol:kotlin-sdk:$version")
 * ```
 *
 * This will transitively include all core, client, and server components.
 */

package io.modelcontextprotocol.kotlin.sdk
