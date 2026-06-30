plugins {
    id("mcp.multiplatform")
    id("mcp.detekt")
    id("mcp.publishing")
    id("mcp.dokka")
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlin-sdk-core"))
                api(libs.ktor.server.core)
                api(libs.ktor.server.sse)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.serialization)
                implementation(libs.kotlin.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":test-utils"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotest.assertions.ktor)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.test.host)

                implementation(libs.logback.classic)
            }
        }
    }
}
