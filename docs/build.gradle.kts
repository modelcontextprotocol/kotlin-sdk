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

// Only run knitCheck when explicitly requested, not as part of build/check
tasks.named("knitCheck") {
    onlyIf {
        gradle.startParameter.taskNames.any {
            it.contains("knitCheck") || it.contains("knit")
        }
    }
}

tasks.clean {
    delete("src")
}
