import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.jreleaser)
    `maven-publish`
}

group = "io.modelcontextprotocol"
version = "0.2.0"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.cio)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.websockets)

    implementation(libs.kotlin.logging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.debug)
}

val sources = tasks.create<Jar>("sourcesJar") {
    from(sourceSets["main"].allSource)
    archiveClassifier.set("sources")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }

    val javadocJar = configureEmptyJavadocArtifact()

    publications.withType(MavenPublication::class).all {
        pom.configureMavenCentralMetadata()
        signPublicationIfKeyPresent()
        artifact(javadocJar)
        artifact(sources)
    }

    repositories {
        maven(url = layout.buildDirectory.dir("staging-deploy"))
    }
}

jreleaser {
    gitRootSearch = true
    strict.set(true)

    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        artifacts.set(true)
    }

    deploy {
        active.set(Active.ALWAYS)
        maven {
            active.set(Active.ALWAYS)
            mavenCentral {
                val ossrh by creating {
                    applyMavenCentralRules = true
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)
                }
            }
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

fun MavenPom.configureMavenCentralMetadata() {
    name by project.name
    description by "Kotlin implementation of the Model Context Protocol (MCP)"
    url by "https://github.com/modelcontextprotocol/kotlin-sdk"

    licenses {
        license {
            name by "MIT License"
            url by "https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE"
            distribution by "repo"
        }
    }

    developers {
        developer {
            id by "Anthropic"
            name by "Anthropic Team"
            organization by "Anthropic"
            organizationUrl by "https://www.anthropic.com"
        }
    }

    scm {
        url by "https://github.com/modelcontextprotocol/kotlin-sdk"
        connection by "scm:git:git://github.com/modelcontextprotocol/kotlin-sdk.git"
        developerConnection by "scm:git:git@github.com:modelcontextprotocol/kotlin-sdk.git"
    }
}

fun configureEmptyJavadocArtifact(): org.gradle.jvm.tasks.Jar {
    val javadocJar by project.tasks.creating(Jar::class) {
        archiveClassifier.set("javadoc")
        // contents are deliberately left empty
        // https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
    }
    return javadocJar
}

fun MavenPublication.signPublicationIfKeyPresent() {
    val keyId = project.getSensitiveProperty("SIGNING_KEY_ID")
    val signingKey = project.getSensitiveProperty("SIGNING_KEY_PRIVATE")
    val signingKeyPassphrase = project.getSensitiveProperty("SIGNING_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        the<SigningExtension>().apply {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)

            sign(this@signPublicationIfKeyPresent)
        }
    }
}

fun Project.getSensitiveProperty(name: String?): String? {
    if (name == null) {
        error("Expected not null property '$name' for publication repository config")
    }

    return project.findProperty(name) as? String
        ?: System.getenv(name)
        ?: System.getProperty(name)
}

infix fun <T> Property<T>.by(value: T) {
    set(value)
}

tasks.create<Jar>("localJar") {
    dependsOn(tasks.jar)

    archiveFileName = "kotlin-sdk.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map {
        it.map { if (it.isDirectory) it else zipTree(it) }
    })
}

tasks.test {
    useJUnitPlatform()
}

abstract class GenerateLibVersionTask @Inject constructor(
    @get:Input val libVersion: String,
    @get:OutputDirectory val sourcesDir: File,
) : DefaultTask() {
    @TaskAction
    fun generate() {
        val sourceFile = File(sourcesDir.resolve("io/modelcontextprotocol/kotlin/sdk"), "LibVersion.kt")

        if (!sourceFile.exists()) {
            sourceFile.parentFile.mkdirs()
            sourceFile.createNewFile()
        }

        sourceFile.writeText(
            """
            package io.modelcontextprotocol.kotlin.sdk

            public const val LIB_VERSION: String = "$libVersion"

            """.trimIndent()
        )
    }
}

dokka {
    moduleName.set("MCP Kotlin SDK")

    dokkaSourceSets.main {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/modelcontextprotocol/kotlin-sdk")
            remoteLineSuffix.set("#L")
            documentedVisibilities(VisibilityModifier.Public)
        }
    }
    dokkaPublications.html {
        outputDirectory.set(project.layout.projectDirectory.dir("docs"))
    }
}

val sourcesDir = File(project.layout.buildDirectory.asFile.get(), "generated-sources/libVersion")

val generateLibVersionTask =
    tasks.register<GenerateLibVersionTask>("generateLibVersion", version.toString(), sourcesDir)

kotlin {
    explicitApi = ExplicitApiMode.Strict

    jvmToolchain(21)

    sourceSets {
        main {
            kotlin.srcDir(generateLibVersionTask.map { it.sourcesDir })
        }
    }
}
