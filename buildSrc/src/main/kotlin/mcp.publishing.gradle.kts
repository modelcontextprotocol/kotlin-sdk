plugins {
    `maven-publish`
    signing
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name.contains("jvm", ignoreCase = true)) {
            artifact(javadocJar)
        }

        pom {
            name = project.name
            description = "Kotlin implementation of the Model Context Protocol (MCP)"
            url = "https://github.com/modelcontextprotocol/kotlin-sdk"

            licenses {
                license {
                    name = "MIT License"
                    url = "https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE"
                    distribution = "repo"
                }
            }

            organization {
                name = "Anthropic"
                url = "https://www.anthropic.com"
            }

            developers {
                developer {
                    id = "JetBrains"
                    name = "JetBrains Team"
                    organization = "JetBrains"
                    organizationUrl = "https://www.jetbrains.com"
                }
            }

            scm {
                url = "https://github.com/modelcontextprotocol/kotlin-sdk"
                connection = "scm:git:git://github.com/modelcontextprotocol/kotlin-sdk.git"
                developerConnection = "scm:git:git@github.com:modelcontextprotocol/kotlin-sdk.git"
            }
        }
    }

    repositories {
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    val gpgKeyName = "GPG_SIGNING_KEY"
    val gpgPassphraseName = "SIGNING_PASSPHRASE"
    val signingKey = providers.environmentVariable(gpgKeyName)
        .orElse(providers.gradleProperty(gpgKeyName))
    val signingPassphrase = providers.environmentVariable(gpgPassphraseName)
        .orElse(providers.gradleProperty(gpgPassphraseName))

    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassphrase.get())
        sign(publishing.publications)
    }
}
