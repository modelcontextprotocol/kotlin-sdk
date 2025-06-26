plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("io.modelcontextprotocol.sample.client.MainKt")
}

group = "org.example"
version = "0.1.0"

dependencies {
    implementation(libs.mcp.kotlin)
    implementation(libs.slf4j.nop)
    implementation(libs.anthropic.jvm)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
