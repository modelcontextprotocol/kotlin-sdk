package io.modelcontextprotocol.kotlin.test.utils

import io.ktor.server.engine.EmbeddedServer

/**
 * Retrieves the actual port that the embedded server is bound to.
 *
 * This function resolves the server's connectors and retrieves the port
 * from the single resolved connector. It is useful for obtaining the
 * port dynamically in cases where the server is bound to a random port.
 *
 * Note: This method assumes the server has exactly one resolved connector.
 *
 * @receiver The embedded server instance
 * @return The port number the server is bound to
 */
public suspend fun EmbeddedServer<*, *>.actualPort(): Int = engine.resolvedConnectors().single().port
