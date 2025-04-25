plugins {
    kotlin("jvm") version "2.1.10"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("io.modelcontextprotocol.sample.client.MainKt")
}


group = "org.example"
version = "0.1.0"

val mcpVersion = "0.4.0"
val slf4jVersion = "2.0.9"
val anthropicVersion = "0.8.0"

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    implementation("org.slf4j:slf4j-nop:$slf4jVersion")
    implementation("com.anthropic:anthropic-java:$anthropicVersion")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
