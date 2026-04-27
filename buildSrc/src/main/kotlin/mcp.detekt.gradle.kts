import dev.detekt.gradle.extensions.FailOnSeverity

plugins {
    id("dev.detekt")
}

detekt {
    config = files("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    parallel = true
    failOnSeverity = FailOnSeverity.Error
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    tasks.named("detekt").configure {
        dependsOn("detektMainJvm")
    }
}
