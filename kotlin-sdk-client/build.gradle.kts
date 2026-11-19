@file:OptIn(ExperimentalWasmDsl::class)

import kotlinx.atomicfu.plugin.gradle.AtomicFUPluginExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("mcp.multiplatform")
    alias(libs.plugins.android.kmp.library)
    id("mcp.detekt")
    id("mcp.publishing")
    id("mcp.dokka")
    id("mcp.abi-validation")
}

// Android KMP compilations do not provide the classes task required by the AtomicFU bytecode transform.
extensions.configure<AtomicFUPluginExtension> {
    transformJvm = false
}

val enableAndroidDeviceTests = providers.gradleProperty("androidDeviceTests").orNull == "true"

kotlin {
    android {
        namespace = "io.modelcontextprotocol.kotlin.sdk.client"
        compileSdk = 35
        minSdk = 28
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        withHostTest {}
        if (enableAndroidDeviceTests) {
            withDeviceTest {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
    }

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

        if (enableAndroidDeviceTests) {
            getByName("androidDeviceTest").dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.ktor.client.cio)
                implementation(libs.okhttp.mockwebserver)
                implementation(libs.okhttp.tls)
            }
        }
    }
}
