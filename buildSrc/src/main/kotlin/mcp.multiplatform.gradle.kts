@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

kotlin {

    compilerOptions {
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
                showStandardStreams = true
                events("failed")
            }
            systemProperty("kotest.output.ansi", "true")
            reports {
                junitXml.required.set(true)
                junitXml.includeSystemOutLog.set(true)
                junitXml.includeSystemErrLog.set(true)
            }
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
}

tasks.named("detekt").configure {
    dependsOn(
        "detektMainJvm",
        "detektCommonMainSourceSet",
        "detektTestJvm",
        "detektCommonTestSourceSet",
    )
}
