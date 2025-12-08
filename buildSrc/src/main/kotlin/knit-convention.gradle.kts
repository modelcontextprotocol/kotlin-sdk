plugins {
    id("org.jetbrains.kotlinx.knit")
}
knit {
    rootDir = projectDir
    files = fileTree(projectDir) {
        include("README.md")
    }
    defaultLineSeparator = "\n"
    siteRoot = "" // Disable site root validation
}
