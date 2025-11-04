plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(dependencies.platform(libs.ktor.bom))
                api(project(":kotlin-sdk-core"))
                api(project(":kotlin-sdk-client"))
                api(project(":kotlin-sdk-server"))
            }
        }
    }
}
