import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity

plugins {
    id("dev.detekt")
}

detekt {
    config = files("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = false
    parallel = true
    failOnSeverity = FailOnSeverity.Error
}

tasks.withType<Detekt>().configureEach {
    exclude("**/generated/**", "**/generated-sources/**", "**/build/**")
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    tasks.named("detekt").configure {
        dependsOn("detektMainJvm")
    }
}
