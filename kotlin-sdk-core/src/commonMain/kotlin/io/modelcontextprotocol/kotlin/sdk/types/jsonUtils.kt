package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

public val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

@OptIn(ExperimentalSerializationApi::class)
public val McpJson: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }
}
