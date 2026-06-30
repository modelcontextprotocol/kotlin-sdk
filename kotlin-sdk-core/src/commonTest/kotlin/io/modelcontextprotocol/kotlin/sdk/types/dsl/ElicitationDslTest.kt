package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.buildElicitRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalMcpApi::class)
class ElicitationDslTest {
    @Test
    fun `buildElicitRequest should build with all fields`() {
        val request = buildElicitRequest {
            message = "Provide info"
            requestedSchema {
                properties {
                    put("email", buildJsonObject { put("type", "string") })
                }
                required = listOf("email")
            }
        }

        request.params.message shouldBe "Provide info"
        val formParams = request.params.shouldBeInstanceOf<ElicitRequestFormParams>()
        formParams.requestedSchema.properties["email"].shouldBeInstanceOf<StringSchema>()
        formParams.requestedSchema.required shouldBe listOf("email")
    }

    @Test
    fun `buildElicitRequest should support direct requestedSchema`() {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = mapOf("name" to StringSchema()),
            required = listOf("name"),
        )
        val request = buildElicitRequest {
            message = "Test"
            requestedSchema(schema)
        }

        val formParams = request.params.shouldBeInstanceOf<ElicitRequestFormParams>()
        formParams.requestedSchema shouldBe schema
    }

    @Test
    fun `ElicitRequestedSchemaBuilder should support direct properties assignment`() {
        val request = buildElicitRequest {
            message = "Test"
            requestedSchema {
                properties(buildJsonObject { put("key", buildJsonObject { put("type", "string") }) })
            }
        }
        val formParams = request.params.shouldBeInstanceOf<ElicitRequestFormParams>()
        formParams.requestedSchema.properties["key"].shouldBeInstanceOf<StringSchema>()
    }

    @Test
    fun `buildElicitRequest should throw if message is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildElicitRequest {
                requestedSchema { properties { put("a", 1) } }
            }
        }
    }

    @Test
    fun `buildElicitRequest should throw if requestedSchema is missing`() {
        shouldThrow<IllegalArgumentException> {
            buildElicitRequest {
                message = "Test"
            }
        }
    }

    @Test
    fun `ElicitRequestedSchemaBuilder should throw if properties are missing`() {
        shouldThrow<IllegalArgumentException> {
            buildElicitRequest {
                message = "Test"
                requestedSchema { }
            }
        }
    }
}
