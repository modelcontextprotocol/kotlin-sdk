plugins {
    id("mcp.multiplatform")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            if (System.getProperty("excludeDockerTests") == "true") {
                filter {
                    excludeTestsMatching("*.typescript.*")
                }
            }
        }
    }
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kotlin-sdk"))
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.json)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.test.host)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.awaitility)
                implementation(libs.testcontainers)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}
