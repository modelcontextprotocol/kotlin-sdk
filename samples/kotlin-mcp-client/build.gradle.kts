plugins {
    kotlin("jvm")
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("io.modelcontextprotocol.sample.client.MainKt")
}

group = "org.example"
version = "0.1.0"

dependencies {
    implementation(project(":"))
    implementation(libs.slf4j.nop)
    implementation(libs.anthropic.java.sdk)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
