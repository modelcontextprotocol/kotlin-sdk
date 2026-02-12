package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi

/**
 * Contextual information made available to server request handlers.
 */
@SubclassOptInRequired(ExperimentalMcpApi::class)
public interface Context {
    public val session: ServerSession
}

@OptIn(ExperimentalMcpApi::class)
internal class ContextImpl(override val session: ServerSession) : Context
