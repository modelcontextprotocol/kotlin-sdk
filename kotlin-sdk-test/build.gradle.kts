plugins {
    id("mcp.multiplatform")
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":kotlin-sdk"))
                implementation(kotlin("test"))
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.test.host)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
