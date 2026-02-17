@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("mcp.multiplatform")
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
                api(kotlin("test"))
                api(libs.kotest.assertions.core)
                api(libs.kotest.assertions.json)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.test)
                api(libs.ktor.server.core)
                api(libs.kotlin.logging)
            }
        }

        jvmMain {
            dependencies {
                api(libs.mockk)
                api(libs.awaitility)
                api(libs.junit.jupiter.params)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
