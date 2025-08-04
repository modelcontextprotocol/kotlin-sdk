@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
    id("mcp.dokka")
    id("mcp.jreleaser")
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
}

kotlin {
    iosArm64(); iosX64(); iosSimulatorArm64()
    watchosX64(); watchosArm64(); watchosSimulatorArm64()
    tvosX64(); tvosArm64(); tvosSimulatorArm64()
    js {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlin-sdk-core"))
                api(libs.ktor.client.cio)
                implementation(libs.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}