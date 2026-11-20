import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

base {
    archivesName = "terminal-window-cmdline-benchmarks"
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
    implementation(project(":vt"))
    implementation(project(":app"))
    implementation(libs.terminal.window)
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("com.psiwray.organization.terminal_window_cmdline.benchmarks.BenchmarksKt")
}

tasks.named<JavaExec>("run") {
    group = "verification"
    description = "Runs parser, screen, and projection benchmarks."
    workingDir = rootProject.projectDir
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "com.psiwray.organization.terminal_window_cmdline.benchmarks")
    }
}
