import org.jreleaser.model.Active

plugins {
    id("org.jreleaser")
    id("mcp.publishing")
}

jreleaser {
    gitRootSearch = true
    strict = true

    signing {
        active = Active.ALWAYS
        armored = true
        artifacts = true
        files = true
    }

    deploy {
        active = Active.ALWAYS
        maven {
            active = Active.ALWAYS
            mavenCentral.create("ossrh") {
                active = Active.ALWAYS
                sign = true
                url = "https://central.sonatype.com/api/v1/publisher"
                applyMavenCentralRules = false
                maxRetries = 240
                stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)

                // workaround: https://github.com/jreleaser/jreleaser/issues/1784
                afterEvaluate {
                    publishing.publications.forEach { publication ->
                        if (publication is MavenPublication) {
                            val pubName = publication.name

                            if (!pubName.contains("jvm", ignoreCase = true)
                                && !pubName.contains("metadata", ignoreCase = true)
                                && !pubName.contains("kotlinMultiplatform", ignoreCase = true)
                            ) {
                                artifactOverride {
                                    artifactId = when {
                                        pubName.contains("wasm", ignoreCase = true) ->
                                            "${project.name}-wasm-${pubName.lowercase().substringAfter("wasm")}"

                                        else -> "${project.name}-${pubName.lowercase()}"
                                    }
                                    jar = false
                                    verifyPom = false
                                    sourceJar = false
                                    javadocJar = false
                                }
                            }
                        }
                    }
                }
            }
        }

        checksum {
            individual = false
            artifacts = false
            files = false
        }
    }

    release {
        github {
            skipRelease = true
            skipTag = true
            overwrite = false
            token = "none"
        }
    }
}
