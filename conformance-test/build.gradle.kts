import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceServerKt")
}

tasks.register<CreateStartScripts>("conformanceClientScripts") {
    mainClass.set("io.modelcontextprotocol.kotlin.sdk.conformance.ConformanceClientKt")
    applicationName = "conformance-client"
    outputDir = tasks.named<CreateStartScripts>("startScripts").get().outputDir
    classpath = tasks.named<Jar>("jar").get().outputs.files + configurations.named("runtimeClasspath").get()
}

tasks.named("installDist") {
    dependsOn("conformanceClientScripts")
}

dependencies {
    implementation(project(":kotlin-sdk"))
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(project(":test-utils"))
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    dependsOn("installDist")

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
