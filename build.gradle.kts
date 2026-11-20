import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isX64 = hostArch == "x86_64" || hostArch == "amd64"
val isArm64 = hostArch == "aarch64" || hostArch == "arm64"
val isMac = hostOs.contains("mac") || hostOs.contains("darwin")
val lwjglNativesClassifier =
    when {
        hostOs.contains("linux") && isX64 -> "natives-linux"
        hostOs.contains("linux") && isArm64 -> "natives-linux-arm64"
        hostOs.contains("windows") && isX64 -> "natives-windows"
        hostOs.contains("windows") && isArm64 -> "natives-windows-arm64"
        isMac && isX64 -> "natives-macos"
        isMac && isArm64 -> "natives-macos-arm64"
        else -> error("Unsupported host for LWJGL natives: $hostOs/$hostArch")
    }

extra["lwjglNativesClassifier"] = lwjglNativesClassifier
extra["runtimeJvmArgs"] =
    buildList {
        add("-Djava.awt.headless=true")
        add("--enable-native-access=ALL-UNNAMED")
        if (isMac) {
            add("-XstartOnFirstThread")
        }
    }

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "settings.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_standard_package-name" to "disabled"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get())
                .editorConfigOverride(mapOf("ktlint_standard_package-name" to "disabled"))
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
                .editorConfigOverride(mapOf("ktlint_standard_package-name" to "disabled"))
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs the integration test suite on the current host."
    dependsOn(":vt:integrationTest", ":pty:integrationTest")
}

tasks.register("fullTest") {
    group = "verification"
    description = "Runs every test, including long-running tests."
    dependsOn(":vt:fullTest", ":pty:test", ":pty:integrationTest", ":app:test")
}
