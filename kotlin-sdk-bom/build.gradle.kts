plugins {
    `java-platform`
    id("mcp.publishing")
}

dependencies {
    constraints {
        api(project(":kotlin-sdk-core"))
        api(project(":kotlin-sdk-client"))
        api(project(":kotlin-sdk-server"))
    }
}
