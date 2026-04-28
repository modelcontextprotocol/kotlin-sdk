@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
    id("mcp.dokka")
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
}

// Generation library versions
val generateLibVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated-sources/libVersion")
    outputs.dir(outputDir)

    // Capture version at configuration time to avoid configuration cache issues
    val projectVersion = project.version.toString()

    doLast {
        val sourceFile = outputDir.get().file("io/modelcontextprotocol/kotlin/sdk/LibVersion.kt").asFile
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(
            """
            package io.modelcontextprotocol.kotlin.sdk

            public const val LIB_VERSION: String = "$projectVersion"
            
            """.trimIndent(),
        )
    }
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
            kotlin.srcDir(generateLibVersion)
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.collections.immutable)
                implementation(libs.ktor.server.websockets)
                implementation(libs.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":test-utils"))
                implementation(kotlin("test"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.junit.jupiter.params)
                implementation(libs.mockk)
                runtimeOnly(libs.slf4j.simple)
            }
        }

        wasmJsMain.dependencies {
            api(libs.kotlinx.coroutines.core.wasm) // workaround for wasm server
        }
    }
}
