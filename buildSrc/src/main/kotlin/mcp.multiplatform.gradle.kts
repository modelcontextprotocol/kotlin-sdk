@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

kotlin {
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_1_8
        tasks.withType<Test> {
            useJUnitPlatform()
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
