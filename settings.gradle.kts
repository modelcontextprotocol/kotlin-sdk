rootProject.name = "kotlin-sdk"

pluginManagement {
    repositories {
        google()
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
        google {
            content {
                includeGroupByRegex("com\\.android\\.tools(\\..*)?")
                includeGroup("androidx.annotation")
                includeGroupByRegex("androidx\\.test(\\..*)?")
                includeGroup("androidx.tracing")
                includeGroup("com.google.testing.platform")
            }
        }
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
