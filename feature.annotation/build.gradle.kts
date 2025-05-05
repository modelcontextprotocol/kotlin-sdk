import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
group = "io.modelcontextprotocol.feature.annotation"
version = "0.5.0"

repositories {
    mavenCentral()
}


dependencies {
    implementation(project(":"))
    implementation(libs.kotlin.reflect)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}