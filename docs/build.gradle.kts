plugins {
    kotlin("jvm")
    alias(libs.plugins.knit)
}

dependencies {
    implementation(project(":kotlin-sdk"))
    implementation(libs.ktor.server.cio)
}

tasks.matching {
    it.name == "ktlintMainSourceSetCheck" || it.name == "ktlintMainSourceSetFormat"
}.configureEach {
    enabled = false
}

knit {
    rootDir = project.rootDir
    files = files(project.rootDir.resolve("README.md"))
    defaultLineSeparator = "\n"
    siteRoot = "" // Disable site root validation
}
