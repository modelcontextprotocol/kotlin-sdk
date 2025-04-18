@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "org.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    jvm {
        binaries {
            executable {
                mainClass.set("Main_jvmKt")
            }
        }
    }
    wasmJs {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
        }
        jvmMain.dependencies {
            implementation("org.slf4j:slf4j-nop:2.0.9")
        }
        wasmJsMain.dependencies {}
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
