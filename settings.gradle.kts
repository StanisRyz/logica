pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Kotlin's JS/Wasm tooling adds its managed Node and Binaryen distribution repositories.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
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
include(":web-app")
