import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation

plugins {
    id("mcp.multiplatform")
}

kotlin {
    jvm {
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kotlin-sdk"))
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.test.host)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.awaitility)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

tasks.register<Test>("conformance") {
    group = "conformance"
    description = "Run MCP conformance tests with detailed output"

    val jvmCompilation = kotlin.targets["jvm"].compilations["test"] as KotlinJvmCompilation
    testClassesDirs = jvmCompilation.output.classesDirs
    classpath = jvmCompilation.runtimeDependencyFiles

    useJUnitPlatform()

    filter {
        includeTestsMatching("*ConformanceTest*")
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    doFirst {
        systemProperty("test.classpath", classpath.asPath)

        println("\n" + "=".repeat(60))
        println("MCP CONFORMANCE TESTS")
        println("=".repeat(60))
        println("These tests validate compliance with the MCP specification.")
        println("=".repeat(60) + "\n")
    }
}
