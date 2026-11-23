package io.modelcontextprotocol.kotlin.sdk.types

import io.kotest.assertions.json.shouldEqualJson
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalMcpApi::class)
class SkillsTest {

    private val listResultJson = """
        {
          "resultType": "complete",
          "skills": [
            {
              "uri": "skill://git-workflow/SKILL.md",
              "frontmatter": {
                "name": "git-workflow",
                "description": "Follow this team's Git conventions for branching and commits"
              },
              "resources": [
                {
                  "uri": "skill://git-workflow/SKILL.md",
                  "digest": "sha256:b95a384300adeea2d902f7d19cd7c04b378ef58e09759107b9c7db4dcacbaa25",
                  "size": 190
                }
              ]
            },
            {
              "uri": "skill://acme/billing/refunds/SKILL.md",
              "frontmatter": {
                "name": "refunds",
                "description": "Process customer refund requests per company policy",
                "license": "Apache-2.0"
              },
              "resources": [
                {
                  "uri": "skill://acme/billing/refunds/SKILL.md",
                  "digest": "sha256:ae7bc7e45f44a6381977ef1f772cbad6e38d8ec78903ea7b87abb373815a8f89",
                  "size": 175
                },
                {
                  "uri": "skill://acme/billing/refunds/examples/email.md",
                  "digest": "sha256:a724fa5ed3b9e39c0e731fe1f0e866eb46d23969af25ecf0d5f41982e3e36bea",
                  "size": 58
                }
              ]
            },
            {
              "uri": "skill://reports/daily/SKILL.md",
              "frontmatter": {
                "name": "daily",
                "description": "Assemble today's operational report from live data"
              },
              "resources": "dynamic"
            }
          ],
          "ttlMs": 300000,
          "cacheScope": "public"
        }
    """.trimIndent()

    private val getResultJson = """
        {
          "resultType": "complete",
          "skill": {
            "uri": "skill://pdf-processing/SKILL.md",
            "frontmatter": {
              "name": "pdf-processing",
              "description": "Extract, fill, and assemble PDF documents"
            },
            "resources": [
              {
                "uri": "skill://pdf-processing/SKILL.md",
                "digest": "sha256:99b737495721155ece826d57521e2d66141ebdc1344a400487481ea2642ab19e",
                "size": 151
              },
              {
                "uri": "skill://pdf-processing/templates/invoice.md",
                "digest": "sha256:61f4ea6d2c75fde1b4977219e7e3107d491c3c26aefb6686e84d6281c088d9ee",
                "size": 29
              }
            ]
          },
          "ttlMs": 300000,
          "cacheScope": "public"
        }
    """.trimIndent()

    @Test
    fun `should round trip the spec skills list result`() {
        val result = assertIs<ListSkillsResult>(McpJson.decodeFromString<RequestResult>(listResultJson))

        assertEquals(listOf("git-workflow", "refunds", "daily"), result.skills.map { it.name })
        assertEquals(JsonPrimitive("Apache-2.0"), result.skills[1].frontmatter["license"])
        assertEquals(2, assertIs<SkillResources.Files>(result.skills[1].resources).files.size)
        assertEquals(SkillResources.Dynamic, result.skills[2].resources)
        assertEquals(300_000, result.ttlMs)
        assertEquals(CacheScope.Public, result.cacheScope)
        assertNull(result.nextCursor)
        McpJson.encodeToString<RequestResult>(result) shouldEqualJson listResultJson
    }

    @Test
    fun `should round trip the spec skills get result`() {
        val result = assertIs<GetSkillResult>(McpJson.decodeFromString<RequestResult>(getResultJson))

        assertEquals("skill://pdf-processing/SKILL.md", result.skill.uri)
        assertEquals("Extract, fill, and assemble PDF documents", result.skill.description)
        McpJson.encodeToString<RequestResult>(result) shouldEqualJson getResultJson
    }

    @Test
    fun `should round trip skills requests`() {
        val listJson = """
            {
              "method": "skills/list",
              "params": {
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities": {}
                }
              }
            }
        """.trimIndent()
        val getJson = """
            {
              "method": "skills/get",
              "params": {
                "uri": "skill://pdf-processing/SKILL.md"
              }
            }
        """.trimIndent()

        val list = assertIs<ListSkillsRequest>(McpJson.decodeFromString<Request>(listJson))
        val get = assertIs<GetSkillRequest>(McpJson.decodeFromString<Request>(getJson))

        assertEquals("2026-07-28", list.params?.meta?.protocolVersion)
        assertEquals("skill://pdf-processing/SKILL.md", get.uri)
        McpJson.encodeToString<Request>(list) shouldEqualJson listJson
        McpJson.encodeToString<Request>(get) shouldEqualJson getJson
    }

    @Test
    fun `should encode required cache fields with defaults`() {
        McpJson.encodeToString(ListSkillsResult(skills = emptyList())) shouldEqualJson """
            {"resultType": "complete", "skills": [], "ttlMs": 0, "cacheScope": "private"}
        """.trimIndent()
    }

    @Test
    fun `should reject skill entries the spec marks invalid`() {
        fun skillWith(resources: String, frontmatter: String = """{"name": "a", "description": "b"}""") =
            """{"uri": "skill://a/SKILL.md", "frontmatter": $frontmatter, "resources": $resources}"""

        assertFailsWith<SerializationException> { McpJson.decodeFromString<Skill>(skillWith("\"static\"")) }
        assertFailsWith<SerializationException> {
            McpJson.decodeFromString<Skill>(
                """{"uri": "skill://a/SKILL.md", "frontmatter": {"name": "a", "description": "b"}}""",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            McpJson.decodeFromString<Skill>(skillWith("\"dynamic\"", """{"name": "a"}"""))
        }
        assertFailsWith<IllegalArgumentException> {
            McpJson.decodeFromString<Skill>(
                skillWith("""[{"uri": "skill://a/SKILL.md", "digest": "sha256:ABC", "size": 1}]"""),
            )
        }
        assertFailsWith<IllegalArgumentException> { ListSkillsResult(skills = emptyList(), ttlMs = -1) }
    }

    @Test
    fun `should declare the skills extension together with the resources capability`() {
        val capabilities = ServerCapabilities().withSkillsExtension(SkillsExtensionSettings(directoryRead = true))

        McpJson.encodeToString(capabilities) shouldEqualJson """
            {
              "resources": {},
              "extensions": {
                "io.modelcontextprotocol/skills": {"directoryRead": true}
              }
            }
        """.trimIndent()
        assertEquals(SkillsExtensionSettings(directoryRead = true), capabilities.skillsExtension)
        assertNull(ServerCapabilities().skillsExtension)
    }

    @Test
    fun `should keep existing resources and extensions when declaring skills`() {
        val ui = buildJsonObject { put("version", 1) }
        val capabilities = ServerCapabilities(
            resources = ServerCapabilities.Resources(subscribe = true),
            extensions = mapOf("io.modelcontextprotocol/ui" to ui),
        ).withSkillsExtension()

        assertEquals(ServerCapabilities.Resources(subscribe = true), capabilities.resources)
        assertEquals(ui, capabilities.extensions?.get("io.modelcontextprotocol/ui"))
        assertEquals(SkillsExtensionSettings(), capabilities.skillsExtension)
    }
}
