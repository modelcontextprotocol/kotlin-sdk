plugins {
    id("mcp.multiplatform")
}

kotlin {
    jvm {
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kotlin-sdk"))
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.serialization)
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":test-utils"))
            }
        }
    }
}
