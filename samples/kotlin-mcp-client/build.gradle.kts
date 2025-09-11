plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("io.modelcontextprotocol.sample.client.MainKt")
}

group = "org.example"
version = "0.1.0"

dependencies {
    implementation(libs.mcp.kotlin)
    implementation(libs.ktor.client.cio)
    implementation(libs.anthropic.java)
    implementation(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
