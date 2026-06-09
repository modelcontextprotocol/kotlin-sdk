plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

group = "org.example"
version = "0.1.0"

application {
    mainClass.set("io.modelcontextprotocol.sample.roots.MainKt")
}

dependencies {
    implementation(libs.mcp.kotlin.client)
    implementation(libs.mcp.kotlin.server)
    implementation(libs.mcp.kotlin.testing)
    implementation(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}