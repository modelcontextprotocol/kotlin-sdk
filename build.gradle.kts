plugins {
    id("mcp.dokka")
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

dependencies {
    dokka(project(":kotlin-sdk-core"))
    dokka(project(":kotlin-sdk-client"))
    dokka(project(":kotlin-sdk-server"))

    kover(project(":kotlin-sdk-core"))
    kover(project(":kotlin-sdk-client"))
    kover(project(":kotlin-sdk-server"))
    "kover"(project(":integration-test"))
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "dev.detekt")

    detekt {
        config = files("$rootDir/detekt.yml")
        buildUponDefaultConfig = true
    }
}

dokka {
    moduleName = "MCP Kotlin SDK"

    dokkaPublications.html {
        includes.from("docs/moduledoc.md")
    }
}

ktlint {
    filter {
        exclude("**/generated*/**")
    }
}

kover {
    reports {
        filters {
            includes.classes("io.modelcontextprotocol.kotlin.sdk.*")
            excludes {
                annotatedBy("kotlin.Deprecated")
                classes("io.modelcontextprotocol.kotlin.sdk.models.*") // temporary
                classes("io.modelcontextprotocol.kotlin.sdk.models.infrastructure.*") // generated
            }
        }
        total {
            log {
            }
            verify {
                rule {
                    minBound(65)
                }
            }
        }
    }
}
