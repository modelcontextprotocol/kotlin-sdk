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

include(
    ":test-utils",
    ":kotlin-sdk-core",
    ":kotlin-sdk-client",
    ":kotlin-sdk-server",
    ":kotlin-sdk-testing",
    ":kotlin-sdk",
    ":integration-test",
    ":docs",
    ":conformance-test",
)
