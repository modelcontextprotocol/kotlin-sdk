@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
    id("mcp.dokka")
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    alias(libs.plugins.openapi.generator)
}

/*
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(tasks.openApiGenerate)
}

tasks.named("runKtlintCheckOverCommonMainSourceSet") {
    dependsOn(tasks.openApiGenerate)
}

// Also ensure it runs before other relevant tasks
tasks.withType<DokkaGenerateTask>().configureEach {
    dependsOn(tasks.openApiGenerate)
}

tasks.withType<Task>().configureEach {
    if (name.lowercase().contains("sourcesjar")) {
        dependsOn(tasks.openApiGenerate)
    }
}
 */

openApiGenerate {
    val schemaVersion = "2025-03-26" // or "2025-06-18" or "draft"
    val schemaUrl =
//        "https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/refs/heads/main/schema/$schemaVersion/schema.json"
        "https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/refs/tags/$schemaVersion/schema/$schemaVersion/schema.json"
    generatorName = "kotlin"
    remoteInputSpec = schemaUrl
    outputDir = layout.buildDirectory.dir("generated-sources/openapi").get().asFile.absolutePath
    packageName = "io.modelcontextprotocol.kotlin.sdk.models"
    modelPackage = "io.modelcontextprotocol.kotlin.sdk.models"
    apiPackage = "io.modelcontextprotocol.kotlin.sdk.api"
    generateModelTests = false
    generateModelDocumentation = false
    cleanupOutput = false
    skipValidateSpec = true // do not validate spec
    library.set("multiplatform")
    ignoreFileOverride.set("${layout.projectDirectory}/.openapi-generator-ignore")
    globalProperties.set(
        mapOf(
            "supportingFiles" to "",
            "models" to "", // or generate all models
        ),
    )
    configOptions.set(
        mapOf(
            "omitGradleWrapper" to "true",
            "enumPropertyNaming" to "UPPERCASE",
            "dateLibrary" to "kotlinx-datetime",
            "explicitApi" to "true",
            "modelMutable" to "false",
        ),
    )
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
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()
    tvosX64()
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
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
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
