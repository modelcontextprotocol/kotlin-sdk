package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi

/**
 * Contextual information made available to server request handlers.
 */
@SubclassOptInRequired(ExperimentalMcpApi::class)
public interface ServerHandlerContext : ServerCommunication {
    public val session: ServerSession
}

@OptIn(ExperimentalMcpApi::class)
internal class ServerHandlerContextImpl(override val session: ServerSession) :
    ServerHandlerContext,
    ServerCommunication by session
