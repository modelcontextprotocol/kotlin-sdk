@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
    id("mcp.dokka")
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    `netty-convention`
}

kotlin {
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
    tvosX64()
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
                implementation(project(":kotlin-sdk-testing"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.server.websockets)
            }
        }

        jvmTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(libs.awaitility)
                implementation(libs.ktor.client.apache5)
                implementation(libs.mockk)
                implementation(libs.junit.jupiter.params)
                implementation(libs.mokksy)
                implementation(dependencies.platform(libs.netty.bom))
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}
