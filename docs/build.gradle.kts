import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("mcp.multiplatform")
}

kotlin {
    jvm()

    explicitApi = ExplicitApiMode.Disabled

    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":kotlin-sdk"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io.core)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.sse)
            }
        }
    }
}

tasks.clean {
    delete("src")
}
