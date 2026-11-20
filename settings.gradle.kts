pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "terminal-window-cmdline"

include(":vt", ":pty", ":app", ":benchmarks")
