
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktlint)
}

group = "no.nav.syfo"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "no.nav.syfo.utenlandsopphold.AppKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

tasks {
    shadowJar {
        mergeServiceFiles()
    }
}

afterEvaluate {
    tasks.shadowJar {
        archiveFileName.set("app.jar")
    }
}
