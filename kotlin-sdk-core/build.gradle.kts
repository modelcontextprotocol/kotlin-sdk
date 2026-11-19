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
    android {
        namespace = "io.modelcontextprotocol.kotlin.sdk.core"
        compileSdk = 35
        minSdk = 28
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
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

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        androidMain.get().dependsOn(jvmAndAndroidMain)

        commonMain {
            kotlin.srcDir(generateLibVersion)
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.collections.immutable)
                api(libs.ktor.websockets)
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
                runtimeOnly(libs.slf4j.simple)
            }
        }

        wasmJsMain.dependencies {
            api(libs.kotlinx.coroutines.core.wasm) // workaround for wasm server
        }
    }
}
