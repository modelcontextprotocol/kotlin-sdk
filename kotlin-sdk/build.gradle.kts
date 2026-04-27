plugins {
    id("mcp.multiplatform")
    id("mcp.detekt")
    id("mcp.publishing")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlin-sdk-core"))
                api(project(":kotlin-sdk-client"))
                api(project(":kotlin-sdk-server"))
            }
        }
    }
}
