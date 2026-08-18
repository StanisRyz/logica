import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "logica-web.js"
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "logica-web.js"
            }
        }
        binaries.executable()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        webMain {
            resources.srcDir(rootProject.layout.projectDirectory.dir("puzzle-data"))
            resources.srcDir(project(":puzzle-core").layout.projectDirectory.dir("src/commonMain/resources"))

            dependencies {
                implementation(project(":puzzle-core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}

val compatibilityDistribution =
    layout.buildDirectory.dir("dist/composeWebCompatibility/productionExecutable")

tasks.register<Zip>("packageYandexDistribution") {
    group = "distribution"
    description = "Builds the JS/Wasm compatibility host as a Yandex Games upload ZIP."
    dependsOn("composeCompatibilityBrowserDistribution")

    from(compatibilityDistribution)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("logica-yandex.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    doFirst {
        val distributionRoot = compatibilityDistribution.get().asFile
        val rootIndex = distributionRoot.resolve("index.html")
        val applicationIndexes =
            distributionRoot
                .walkTopDown()
                .filter { it.isFile && it.name == "index.html" }
                .toList()
        check(applicationIndexes.size == 1 && applicationIndexes.single().canonicalFile == rootIndex.canonicalFile) {
            "The Yandex distribution must contain exactly one application index.html at its root."
        }

        listOf(
            "levels/v1/checksums.sha256",
            "sudoku/v1/easy.sdk",
            "word/v1/allowed_guesses.txt",
            "word/v2/answers.txt",
        ).forEach { path ->
            check(distributionRoot.resolve(path).isFile) {
                "The Yandex distribution is missing canonical asset $path."
            }
        }
    }

    doLast {
        val distributionRoot = compatibilityDistribution.get().asFile
        val uncompressedBytes = distributionRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val archive = archiveFile.get().asFile
        logger.lifecycle(
            "Yandex distribution: $uncompressedBytes bytes uncompressed, " +
                "${archive.length()} bytes compressed at ${archive.absolutePath}",
        )
    }
}
