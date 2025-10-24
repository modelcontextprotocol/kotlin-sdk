plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "org.example"
version = "0.1.0"

application {
    mainClass.set("io.modelcontextprotocol.sample.server.MainKt")
}

dependencies {
    implementation(libs.mcp.kotlin.server)
    implementation(libs.ktor.server.cio)
    implementation(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
