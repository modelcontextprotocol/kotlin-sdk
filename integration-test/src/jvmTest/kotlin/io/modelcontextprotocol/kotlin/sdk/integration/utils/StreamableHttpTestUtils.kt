package io.modelcontextprotocol.kotlin.sdk.integration.utils

import io.ktor.http.Headers
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Utility functions for StreamableHTTP transport integration tests.
 *
 * These utilities help with:
 * - Session ID validation and extraction
 * - HTTP header manipulation
 * - Test assertions for HTTP responses
 *
 * Note: Some utilities are placeholders for future advanced test scenarios
 * that require more complex test infrastructure.
 */
@OptIn(ExperimentalUuidApi::class)
object StreamableHttpTestUtils {

    private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
    private const val MCP_PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
    private const val MCP_RESUMPTION_TOKEN_HEADER = "last-event-id"

    /**
     * Validates that a session ID is in the correct format (UUID).
     *
     * @param sessionId The session ID to validate
     * @return true if the session ID is a valid UUID, false otherwise
     */
    fun validateSessionId(sessionId: String): Boolean = try {
        Uuid.parse(sessionId)
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    /**
     * Extracts the MCP session ID from HTTP headers.
     *
     * @param headers The HTTP headers to search
     * @return The session ID if present, null otherwise
     */
    fun extractSessionIdFromHeaders(headers: Headers): String? = headers[MCP_SESSION_ID_HEADER]

    /**
     * Extracts the MCP protocol version from HTTP headers.
     *
     * @param headers The HTTP headers to search
     * @return The protocol version if present, null otherwise
     */
    fun extractProtocolVersionFromHeaders(headers: Headers): String? = headers[MCP_PROTOCOL_VERSION_HEADER]

    /**
     * Extracts the Last-Event-ID (resumption token) from HTTP headers.
     *
     * @param headers The HTTP headers to search
     * @return The last event ID if present, null otherwise
     */
    fun extractLastEventIdFromHeaders(headers: Headers): String? = headers[MCP_RESUMPTION_TOKEN_HEADER]

    /**
     * Generates a random session ID in UUID format.
     *
     * @return A new UUID string
     */
    fun generateSessionId(): String = Uuid.random().toString()

    /**
     * Validates that a message is within the StreamableHTTP size limit (4MB).
     *
     * @param messageSize The size of the message in bytes
     * @return true if the message is within the limit, false otherwise
     */
    fun isWithinMessageSizeLimit(messageSize: Int): Boolean = messageSize <= MAXIMUM_MESSAGE_SIZE

    /**
     * Gets the maximum message size allowed by StreamableHTTP transport.
     *
     * @return The maximum message size in bytes (4MB)
     */
    fun getMaximumMessageSize(): Int = MAXIMUM_MESSAGE_SIZE

    private const val MAXIMUM_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB

    // TODO: Future utilities for advanced testing scenarios

    // TODO: Creates a custom HTTP client with specific headers for testing.
    //
    // This would be useful for testing:
    // - Invalid session IDs
    // - Custom Host/Origin headers for DNS rebinding tests
    // - Missing required headers
    //
    // Implementation requires creating a Ktor HttpClient with custom configuration.
    // fun createCustomHttpClient(customHeaders: Map<String, String>): HttpClient

    // TODO: Sends a direct HTTP request to the server for low-level testing.
    //
    // This would be useful for testing:
    // - Malformed requests
    // - Invalid JSON
    // - HTTP-level errors (404, 403, 400, etc.)
    //
    // Implementation requires direct Ktor client usage outside of MCP client abstraction.
    // suspend fun sendDirectHttpRequest(url: String, method: HttpMethod, headers: Map<String, String>, body: String?): HttpResponse

    // TODO: Creates a StreamableHTTP client with a pre-set session ID.
    //
    // This would be useful for testing:
    // - Session resumption
    // - Invalid session ID handling
    // - Session expiration scenarios
    //
    // Implementation requires ability to inject session ID into client transport.
    // fun createClientWithSessionId(url: String, sessionId: String): Client

    // TODO: Asserts that a request was rejected with a specific HTTP status code.
    //
    // This would be useful for testing:
    // - Authorization failures (403)
    // - Invalid requests (400)
    // - Not found errors (404)
    //
    // Implementation requires catching and analyzing transport-level errors.
    // suspend fun assertRejectedWithStatus(expectedStatus: HttpStatusCode, block: suspend () -> Unit)
}
