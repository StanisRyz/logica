pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RuStore publishes its SDKs only here, never to Maven Central.
        maven("https://artifactory-external.vkpartner.ru/artifactory/maven-rustore-exposed/") {
            content { includeGroup("ru.rustore.sdk") }
        }
    }
}

rootProject.name = "Logica"
include(":app")
include(":puzzle-core")
