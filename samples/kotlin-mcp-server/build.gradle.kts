@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
            implementation(libs.mcp.kotlin)
            implementation(libs.ktor.server.cio)
        }
        jvmMain.dependencies {
            implementation(libs.slf4j.simple)
        }
        wasmJsMain.dependencies {}
    }
}
