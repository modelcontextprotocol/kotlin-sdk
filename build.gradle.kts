plugins {
    id("mcp.dokka")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

allprojects {
    group = "io.modelcontextprotocol"
    version = "0.7.2-alpha1"
}

dependencies {
    dokka(project(":kotlin-sdk-core"))
    dokka(project(":kotlin-sdk-client"))
    dokka(project(":kotlin-sdk-server"))

    kover(project(":kotlin-sdk-core"))
    kover(project(":kotlin-sdk-client"))
    kover(project(":kotlin-sdk-server"))
    kover(project(":kotlin-sdk-test"))
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

dokka {
    moduleName = "MCP Kotlin SDK"

    dokkaPublications.html {
        includes.from("docs/moduledoc.md")
    }
}

kover {
    reports {
        filters {
            includes.classes("io.modelcontextprotocol.kotlin.sdk.*")
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
