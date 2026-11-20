import org.gradle.api.plugins.jvm.JvmTestSuite
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `java-library`
}

base {
    archivesName = "terminal-vt"
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
                        maxParallelForks = 1
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

val fullUnitTest =
    tasks.register<Test>("fullUnitTest") {
        group = "verification"
        description = "Runs every unit test, including long-running tests."
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform()
    }

val fullIntegrationTest =
    tasks.register<Test>("fullIntegrationTest") {
        group = "verification"
        description = "Runs every integration test, including long-running tests."
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = 1
        shouldRunAfter(tasks.test)
    }

tasks.register("fullTest") {
    group = "verification"
    description = "Runs every unit and integration test, including long-running tests."
    dependsOn(fullUnitTest, fullIntegrationTest)
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
        attributes("Automatic-Module-Name" to "com.psiwray.organization.terminal_window_cmdline.vt")
    }
}
