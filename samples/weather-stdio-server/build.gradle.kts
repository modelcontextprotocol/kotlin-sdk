plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("io.modelcontextprotocol.sample.server.MainKt")
}

group = "org.example"
version = "0.1.0"

dependencies {
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.mcp.kotlin.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.cio)
    implementation(libs.slf4j.simple)
    runtimeOnly(libs.kotlin.logging)
    runtimeOnly(libs.kotlinx.collections.immutable)

    testImplementation(kotlin("test"))

    testImplementation(libs.mcp.kotlin.client)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
