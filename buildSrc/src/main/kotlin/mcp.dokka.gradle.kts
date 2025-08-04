import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    moduleName.set("MCP Kotlin SDK - ${project.name}")

    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory = projectDir.resolve("src")
            remoteUrl("https://github.com/modelcontextprotocol/kotlin-sdk/tree/main/${project.name}/src")
            remoteLineSuffix = "#L"
        }

        documentedVisibilities(VisibilityModifier.Public)
    }

    dokkaPublications.html {
        outputDirectory = rootProject.layout.projectDirectory.dir("docs/${project.name}")
    }
}