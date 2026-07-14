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
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.auth)

    implementation(ktorLibs.serialization.jackson)

    implementation(libs.isyfo.backend.common)

    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikariCP)
    implementation(libs.flyway.database.postgresql)

    // Kafka
    implementation(libs.kafka)

    // Metrics
    implementation(libs.micrometer.registry.prometheus)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.client.mock)
    testImplementation(testFixtures(libs.isyfo.backend.common))
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation(libs.embedded.postgres)
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:${libs.versions.postgres.embedded.runtime.get()}"))
    testImplementation(libs.mockk)
}

tasks {
    register("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
        mergeServiceFiles()
        archiveFileName.set("app.jar")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
    }
}

afterEvaluate {
    tasks.shadowJar {
        archiveFileName.set("app.jar")
    }
}
