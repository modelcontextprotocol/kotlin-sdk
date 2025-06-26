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
    implementation(libs.mcp.kotlin)
    implementation(libs.slf4j.nop)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
