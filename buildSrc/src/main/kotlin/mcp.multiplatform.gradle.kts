@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

// Generation library versions
val generateLibVersion by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated-sources/libVersion")
    outputs.dir(outputDir)

    doLast {
        val sourceFile = outputDir.get().file("io/modelcontextprotocol/kotlin/sdk/LibVersion.kt").asFile
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(
            """
            package io.modelcontextprotocol.kotlin.sdk

            public const val LIB_VERSION: String = "${project.version}"
            
            """.trimIndent(),
        )
    }
}

kotlin {

    compilerOptions {
        languageVersion = KOTLIN_2_1
        apiVersion = KOTLIN_2_1
        // TODO: allWarningsAsErrors = true
        extraWarnings = true
        freeCompilerArgs =
            listOf(
                "-Xwhen-guards",
            )
    }
    coreLibrariesVersion = "2.1.21"

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
            javaParameters = true
        }
    }

    macosX64()
    macosArm64()

    linuxX64()
    linuxArm64()

    mingwX64()

    js { nodejs() }
    wasmJs { nodejs() }

    explicitApi = ExplicitApiMode.Strict
    jvmToolchain(21)

    sourceSets {
        commonMain {
            kotlin.srcDir(generateLibVersion)
        }
    }
}
