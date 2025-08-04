plugins {
    alias(libs.plugins.ktlint)
}

allprojects {
    group = "io.modelcontextprotocol"
    version = "0.6.0"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
