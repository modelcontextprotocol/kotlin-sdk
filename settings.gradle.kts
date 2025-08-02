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

rootProject.name = "kotlin-sdk"

include(":kotlin-sdk-core")
include(":kotlin-sdk-client")
include(":kotlin-sdk-server")
include(":kotlin-sdk")
include(":kotlin-sdk-test")
