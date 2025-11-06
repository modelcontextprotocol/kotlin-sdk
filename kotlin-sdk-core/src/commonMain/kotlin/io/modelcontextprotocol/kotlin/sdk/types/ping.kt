package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ping")
public data class PingRequest(override val params: BaseRequestParams? = null) : ServerRequest
