rootProject.name = "kotlin-mcp-server"

plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            val mcpKotlinVersion = providers.gradleProperty(
                "mcp.kotlin.overrideVersion",
            ).orNull
            if (mcpKotlinVersion != null) {
                logger.lifecycle("Using the override version $mcpKotlinVersion of MCP Kotlin SDK")
                version("mcp-kotlin", mcpKotlinVersion)
            }
        }
    }
}
