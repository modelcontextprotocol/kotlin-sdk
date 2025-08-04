plugins {
    id("mcp.multiplatform")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kotlin-sdk"))
                implementation(kotlin("test"))
                implementation(libs.ktor.server.test.host)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.kotlin.logging)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}