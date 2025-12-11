plugins {
    kotlin("jvm")
    alias(libs.plugins.knit)
}

dependencies {
    implementation(project(":kotlin-sdk"))
    implementation(libs.ktor.server.cio)
}

ktlint {
    filter {
        exclude("src/")
    }
}

tasks.clean {
    dependsOn("knitClean")
    delete("src")
}

knit {
    rootDir = project.rootDir
    files = files(project.rootDir.resolve("README.md"))
    defaultLineSeparator = "\n"
    siteRoot = "" // Disable site root validation
}
