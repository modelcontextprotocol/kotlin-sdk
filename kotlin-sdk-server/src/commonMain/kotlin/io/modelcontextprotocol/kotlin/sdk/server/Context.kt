package io.modelcontextprotocol.kotlin.sdk.server

/**
 * Contextual information made available to server request handlers.
 */
public interface Context {
    public val session: ServerSession
}

internal class ContextImpl(override val session: ServerSession) : Context
