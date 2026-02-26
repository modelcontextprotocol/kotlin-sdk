package io.modelcontextprotocol.kotlin.sdk.types.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.buildElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.invoke
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
        request.params.requestedSchema.properties["email"] shouldBe buildJsonObject { put("type", "string") }
        request.params.requestedSchema.required shouldBe listOf("email")
    }

    @Test
    fun `buildElicitRequest should support direct requestedSchema`() {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject { put("name", buildJsonObject { put("type", "string") }) },
            required = listOf("name"),
        )
        val request = buildElicitRequest {
            message = "Test"
            requestedSchema(schema)
        }

        request.params.requestedSchema shouldBe schema
    }

    @Test
    fun `buildElicitRequest should support direct properties assignment`() {
        val props = buildJsonObject { put("key", "value") }
        val request = buildElicitRequest {
            message = "Test"
            requestedSchema {
                properties(props)
            }
        }
        request.params.requestedSchema.properties shouldBe props
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
    fun `buildElicitRequest should throw if properties are missing`() {
        shouldThrow<IllegalArgumentException> {
            buildElicitRequest {
                message = "Test"
                requestedSchema { }
            }
        }
    }

    @Test
    fun `ElicitRequest should build with all fields`() {
        val request = ElicitRequest {
            message = "Provide info"
            requestedSchema {
                properties {
                    put("email", buildJsonObject { put("type", "string") })
                }
                required = listOf("email")
            }
        }

        request.params.message shouldBe "Provide info"
        request.params.requestedSchema.properties["email"] shouldBe buildJsonObject { put("type", "string") }
        request.params.requestedSchema.required shouldBe listOf("email")
    }

    @Test
    fun `ElicitRequest should support direct requestedSchema`() {
        val schema = ElicitRequestParams.RequestedSchema(
            properties = buildJsonObject { put("name", buildJsonObject { put("type", "string") }) },
            required = listOf("name"),
        )
        val request = ElicitRequest {
            message = "Test"
            requestedSchema(schema)
        }

        request.params.requestedSchema shouldBe schema
    }

    @Test
    fun `ElicitRequestedSchemaBuilder should support direct properties assignment`() {
        val props = buildJsonObject { put("key", "value") }
        val request = ElicitRequest {
            message = "Test"
            requestedSchema {
                properties(props)
            }
        }
        request.params.requestedSchema.properties shouldBe props
    }

    @Test
    fun `ElicitRequest should throw if message is missing`() {
        shouldThrow<IllegalArgumentException> {
            ElicitRequest {
                requestedSchema { properties { put("a", 1) } }
            }
        }
    }

    @Test
    fun `ElicitRequest should throw if requestedSchema is missing`() {
        shouldThrow<IllegalArgumentException> {
            ElicitRequest {
                message = "Test"
            }
        }
    }

    @Test
    fun `ElicitRequestedSchemaBuilder should throw if properties are missing`() {
        shouldThrow<IllegalArgumentException> {
            ElicitRequest {
                message = "Test"
                requestedSchema { }
            }
        }
    }
}
