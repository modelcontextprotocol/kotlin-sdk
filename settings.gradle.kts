pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-sdk"

include(":samples:kotlin-mcp-server")
include(":samples:kotlin-mcp-client")
include(":samples:weather-stdio-server")
