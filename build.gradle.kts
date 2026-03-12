import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.nio.file.Paths

group = "io.github.sndnv"
version = "1.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

plugins {
    val versionKotlin = "2.3.10"
    val versionKotlinxBenchmark = "0.4.15"
    val versionKotest = "6.1.6"

    kotlin("jvm") version versionKotlin
    kotlin("plugin.allopen") version versionKotlin
    id("io.kotest") version versionKotest
    id("org.jetbrains.kotlinx.benchmark") version versionKotlinxBenchmark

    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.kover") version "0.9.7"
    id("com.vanniktech.maven.publish") version "0.36.0"

    id("info.solidsoft.pitest") version "1.19.0-rc.3"
}

object Versions {
    const val kotlinxBenchmark = "0.4.15"
    const val kotest = "6.1.2"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
    testImplementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
    testImplementation("io.kotest:kotest-extensions-pitest:${Versions.kotest}")
}

sourceSets {
    create("benchmarks") {
        dependencies {
            "benchmarksImplementation"("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:${Versions.kotlinxBenchmark}")
        }
    }
}

kotlin {
    jvmToolchain(21)

    target {
        compilations.getByName("benchmarks")
            .associateWith(compilations.getByName("main"))
    }
}

tasks.test {
    useJUnitPlatform()
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("benchmarks")
    }

    configurations {
        named("main") {
            iterations = 3
            warmups = 2
        }
        register("extended") {
            iterations = 10
            warmups = 5
        }
    }
}

allprojects {
    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
            showStandardStreams = true
            events("passed", "skipped", "failed", "standardOut", "standardError")
        }
    }
}

tasks.register("qa") {
    dependsOn("check", "koverPrintCoverage", "koverVerify", "koverHtmlReport", "pitest")
}

kover {
    useJacoco()

    reports {
        filters {
            excludes {
                classes(
                    "io.github.sndnv.fsi.Generators",
                    "io.github.sndnv.fsi.IndexBenchmark",
                    "io.github.sndnv.fsi.IndexBenchmarkSetup"
                )
            }
        }

        verify.rule {
            minBound(99)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    signAllPublications()

    coordinates(group.toString(), name, version.toString())

    pom {
        name = "fsi"
        description = "Library providing simple data structures for efficiently associating information with file system paths"
        inceptionYear = "2026"
        url = "https://github.com/sndnv/fsi"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "sndnv"
                name = "Angel Sanadinov"
                url = "https://github.com/sndnv"
            }
        }
        scm {
            url = "https://github.com/sndnv/fsi"
            connection = "scm:git:git://github.com/sndnv/fsi.git"
            developerConnection = "scm:git:ssh://git@github.com/sndnv/fsi.git"
        }
    }
}

configure<PitestPluginExtension> {
    targetClasses = listOf("io.github.sndnv.fsi*")
    avoidCallsTo = listOf("kotlin.jvm.internal")

    mutationThreshold = 80
    testStrengthThreshold = 90

    reportDir = Paths.get("build/pitest").toFile()
}
