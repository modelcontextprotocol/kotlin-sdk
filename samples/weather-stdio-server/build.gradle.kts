plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("io.modelcontextprotocol.sample.server.MainKt")
}

repositories {
    mavenCentral()
}

group = "org.example"
version = "0.1.0"

dependencies {
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.mcp.kotlin)
    runtimeOnly(libs.ktor.server.cio)
    runtimeOnly(libs.ktor.client.cio)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
