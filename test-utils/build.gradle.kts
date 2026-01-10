plugins {
    kotlin("jvm")
}

kotlin {
    explicitApi()

    dependencies {
        api(kotlin("test"))
        api(libs.kotest.assertions.core)
        api(libs.kotest.assertions.json)
        api(libs.kotlinx.coroutines.test)
        api(libs.mockk)
        api(libs.awaitility)
        api(libs.junit.jupiter.params)
        runtimeOnly(libs.slf4j.simple)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
