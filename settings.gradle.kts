rootProject.name = "kotlin-sdk"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":kotlin-sdk-core")
include(":kotlin-sdk-client")
include(":kotlin-sdk-server")
include(":kotlin-sdk")
include(":kotlin-sdk-test")

// Include sample projects as composite builds
includeBuild("samples/kotlin-mcp-client")
includeBuild("samples/kotlin-mcp-server")
includeBuild("samples/weather-stdio-server")
