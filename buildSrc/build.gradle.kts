plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.kotlin.serialization)
    implementation(libs.kotlinx.atomicfu.gradle)
    implementation(libs.dokka.gradle)
    implementation(libs.maven.publish)
}
