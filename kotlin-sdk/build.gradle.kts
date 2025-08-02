plugins {
    id("mcp.multiplatform")
    id("mcp.publishing")
    id("mcp.dokka")
    id("mcp.jreleaser")
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
