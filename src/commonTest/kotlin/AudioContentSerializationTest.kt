package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioContentSerializationTest {

    private val audioContentJson = buildJsonObject {
        put("data", "base64-encoded-audio-data")
        put("mimeType", "audio/wav")
        put("type", "audio")
    }

    private val audioContent = AudioContent(
        data = "base64-encoded-audio-data",
        mimeType = "audio/wav"
    )

    @Test
    fun `should serialize audio content`() {
        val actual = McpJson.encodeToJsonElement(audioContent)
        assertEquals(
            audioContentJson,
            actual,
            "Expected $actual to be equal to $audioContentJson"
        )
    }

    @Test
    fun `should deserialize audio content`() {
        val content = McpJson.decodeFromJsonElement<AudioContent>(audioContentJson)
        assertEquals(expected = audioContent, actual = content)
    }
}