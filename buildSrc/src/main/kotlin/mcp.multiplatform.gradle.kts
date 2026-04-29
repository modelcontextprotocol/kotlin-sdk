@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

kotlin {

    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_1
        apiVersion = KotlinVersion.KOTLIN_2_1
        freeCompilerArgs =
            listOf(
                "-Wextra",
                "-Xmulti-dollar-interpolation",
            )
    }
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            javaParameters = true
            jvmDefault = JvmDefaultMode.ENABLE
            freeCompilerArgs.addAll(
                "-Xdebug",
            )
        }

        tasks.withType<Test>().configureEach {

            useJUnitPlatform()

            maxParallelForks = Runtime.getRuntime().availableProcessors()
            forkEvery = 100
            testLogging {
                exceptionFormat = TestExceptionFormat.SHORT
                events("failed")
            }
            systemProperty("kotest.output.ansi", "true")
        }
    }
    macosArm64()
    linuxX64()
    linuxArm64()
    mingwX64()
    js { nodejs() }
    wasmJs { nodejs() }

    explicitApi = ExplicitApiMode.Strict
    jvmToolchain(21)
}
