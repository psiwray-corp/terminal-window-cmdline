import org.gradle.api.plugins.jvm.JvmTestSuite
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `java-library`
}

val runtimeJvmArgs =
    (rootProject.extra["runtimeJvmArgs"] as List<*>).map { it.toString() }

base {
    archivesName = "terminal-pty"
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("long")
    }
    jvmArgs(runtimeJvmArgs)
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(libs.kotlin.test)
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }

            targets {
                all {
                    testTask.configure {
                        useJUnitPlatform {
                            excludeTags("long")
                        }
                        jvmArgs(runtimeJvmArgs)
                        maxParallelForks = 1
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

dokka {
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        documentedVisibilities.set(setOf(VisibilityModifier.Public))
        reportUndocumented.set(true)
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "com.psiwray.organization.terminal_window_cmdline.pty")
    }
}
