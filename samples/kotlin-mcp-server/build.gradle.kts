@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "org.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

val jvmMainClass = "Main_jvmKt"

kotlin {
    jvmToolchain(17)
    jvm {
        binaries {
            executable {
                mainClass.set(jvmMainClass)
            }
        }
        val jvmJar by tasks.getting(org.gradle.jvm.tasks.Jar::class) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            doFirst {
                manifest {
                    attributes["Main-Class"] = jvmMainClass
                }

                from(configurations["jvmRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) })
            }
        }
    }
    wasmJs {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":"))
        }
        jvmMain.dependencies {
            implementation("org.slf4j:slf4j-nop:2.0.9")
        }
        wasmJsMain.dependencies {}
    }
}

// Disable WASM optimization to avoid binaryen issues
tasks.matching { it.name.contains("WasmJsOptimize") }.configureEach {
    enabled = false
}
