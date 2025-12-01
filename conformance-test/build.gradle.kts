import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(project(":kotlin-sdk"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.logging)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.websockets)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()

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
