package io.modelcontextprotocol.kotlin.sdk

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioContentSerializationTest {

    private val audioContentJson = """
        {
          "data": "base64-encoded-audio-data",
           "mimeType": "audio/wav",
          "type": "audio"
        }
    """.trimIndent()

    private val audioContent = AudioContent(
        data = "base64-encoded-audio-data",
        mimeType = "audio/wav"
    )

    @Test
    fun `should serialize audio content`() {
        McpJson.encodeToString(audioContent) shouldEqualJson audioContentJson
    }

    @Test
    fun `should deserialize audio content`() {
        val content = McpJson.decodeFromString<AudioContent>(audioContentJson)
        assertEquals(expected = audioContent, actual = content)
    }
}