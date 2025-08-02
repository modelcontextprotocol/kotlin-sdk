plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
    id("mcp.dokka")
    id("mcp.jreleaser")
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.io.core)
                api(libs.ktor.server.websockets)
                api(libs.kotlinx.collections.immutable)
                implementation(libs.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.json)
            }
        }
    }
}