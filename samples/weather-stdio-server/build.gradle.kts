plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("io.modelcontextprotocol.sample.server.MainKt")
}


group = "org.example"
version = "0.1.0"

dependencies {
    implementation(project(":"))
    implementation(libs.slf4j.nop)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
