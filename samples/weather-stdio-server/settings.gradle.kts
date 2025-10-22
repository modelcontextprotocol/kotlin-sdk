rootProject.name = "weather-stdio-server"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
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
