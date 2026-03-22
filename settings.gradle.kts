pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.jellyfin.org/releases") }
    }
}

rootProject.name = "openemby_tv"
include(":app")

// include(":decoder_ffmpeg")
// project(":decoder_ffmpeg").projectDir = file("libraries/decoder_ffmpeg")