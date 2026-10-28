@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("mcp.multiplatform")
    id("mcp.detekt")
    id("mcp.publishing")
    id("mcp.dokka")
    id("mcp.abi-validation")
}

kotlin {
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    watchosArm64()
    watchosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
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
                api(libs.ktor.client.core)
                implementation(libs.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        jvmTest {
            dependencies {
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}
