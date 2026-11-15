plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

group = "org.example"
version = "0.1.0"

application {
    mainClass.set("io.modelcontextprotocol.sample.client.MainKt")
}

dependencies {
    implementation(dependencies.platform(libs.ktor.bom))
    implementation(libs.mcp.kotlin.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.cio)
    implementation(libs.slf4j.simple)

    testImplementation(libs.mcp.kotlin.server)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
