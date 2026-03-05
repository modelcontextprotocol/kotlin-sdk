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
    implementation(libs.ktor.client.auth)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.slf4j.simple)
}
