plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

application {
    mainClass.set("MainKt")
}


group = "org.example"
version = "0.1.0"

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk:0.4.0")
    implementation("org.slf4j:slf4j-nop:2.0.9")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
