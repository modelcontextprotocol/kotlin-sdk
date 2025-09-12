
plugins {
    kotlin("jvm") apply false
    id("mcp.dokka")
}

dependencies {
    dokka(project(":kotlin-sdk-core"))
    dokka(project(":kotlin-sdk-client"))
    dokka(project(":kotlin-sdk-server"))
}

dokka {

    moduleName.set("MCP Kotlin SDK")

    dokkaPublications.html {
    }
}
