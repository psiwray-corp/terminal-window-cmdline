import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    application
}

val lwjglVersion = libs.versions.lwjgl.get()
val lwjglNativesClassifier = rootProject.extra["lwjglNativesClassifier"] as String
val runtimeJvmArgs =
    (rootProject.extra["runtimeJvmArgs"] as List<*>).map { it.toString() }

base {
    archivesName = "terminal-window-cmdline"
}

val natives =
    configurations.create("natives") {
        description = "LWJGL natives for the build host"
        isCanBeConsumed = false
        isCanBeResolved = true
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
    implementation(project(":pty"))
    implementation(libs.terminal.window)
    implementation(kotlin("stdlib"))

    natives("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNativesClassifier")
    natives("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNativesClassifier")
    natives("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNativesClassifier")
    natives("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNativesClassifier")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

configurations.named("runtimeClasspath") {
    extendsFrom(natives)
}

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val platformName =
    when {
        hostOs.contains("linux") && hostArch in setOf("x86_64", "amd64") -> "linux-x64"
        hostOs.contains("linux") && hostArch in setOf("aarch64", "arm64") -> "linux-arm64"
        hostOs.contains("windows") && hostArch in setOf("x86_64", "amd64") -> "windows-x64"
        hostOs.contains("windows") && hostArch in setOf("aarch64", "arm64") -> "windows-arm64"
        (hostOs.contains("mac") || hostOs.contains("darwin")) && hostArch in setOf("x86_64", "amd64") -> "macos-x64"
        (hostOs.contains("mac") || hostOs.contains("darwin")) && hostArch in setOf("aarch64", "arm64") -> "macos-arm64"
        else -> error("Unsupported host for application distribution: $hostOs/$hostArch")
    }

application {
    applicationName = "terminal-window-cmdline"
    mainClass.set("com.psiwray.organization.terminal_window_cmdline.app.MainKt")
    applicationDefaultJvmArgs = runtimeJvmArgs
}

distributions {
    main {
        distributionBaseName.set("terminal-window-cmdline-$platformName")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("long")
    }
    jvmArgs(runtimeJvmArgs)
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
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Automatic-Module-Name" to "com.psiwray.organization.terminal_window_cmdline.app",
        )
    }
}
