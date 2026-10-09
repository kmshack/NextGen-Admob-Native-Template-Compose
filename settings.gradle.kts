pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.4.1"
        id("com.android.library") version "9.4.1"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://artifact.bytedance.com/repository/pangle/") {
            content {
                includeGroup("com.pangle.global")
            }
        }
    }
}

rootProject.name = "NextGen-Admob-Native-Template-Compose"
include(":sample")
