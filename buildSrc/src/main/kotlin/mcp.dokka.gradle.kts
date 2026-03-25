import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    id("org.jetbrains.dokka")
}

dokka {

    dokkaSourceSets.configureEach {
        includes.from("Module.md")

        pluginsConfiguration.html {
            footerMessage = "Copyright © 2024-2025 Anthropic, PBC"
        }

        sourceLink {
            localDirectory = projectDir.resolve("src")
            remoteUrl("https://github.com/modelcontextprotocol/kotlin-sdk/tree/main/${project.name}/src")
            remoteLineSuffix = "#L"
        }

        documentedVisibilities(VisibilityModifier.Public)

        externalDocumentationLinks.register("ktor-client") {
            url("https://api.ktor.io/ktor-client/")
            packageListUrl("https://api.ktor.io/package-list")
        }

        externalDocumentationLinks.register("kotlinx-coroutines") {
            url("https://kotlinlang.org/api/kotlinx.coroutines/")
            packageListUrl("https://kotlinlang.org/api/kotlinx.coroutines/package-list")
        }

        externalDocumentationLinks.register("kotlinx-schema") {
            url("https://kotlin.github.io/kotlinx-schema/")
            packageListUrl("https://kotlin.github.io/kotlinx-schema/package-list")
        }

        externalDocumentationLinks.register("kotlinx-serialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
            packageListUrl("https://kotlinlang.org/api/kotlinx.serialization/package-list")
        }
    }
}
