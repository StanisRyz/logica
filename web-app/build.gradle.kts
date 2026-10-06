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
        commonMain.dependencies {
            implementation(compose.components.resources)
        }

        webMain {
            resources.srcDir(rootProject.layout.projectDirectory.dir("puzzle-data"))
            resources.srcDir(project(":puzzle-core").layout.projectDirectory.dir("src/commonMain/resources"))

            dependencies {
                implementation(project(":platform-contracts"))
                implementation(project(":puzzle-core"))
                implementation(project(":shared-ui"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }

        webTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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
    // Source maps help nobody on the portal and only add to the upload.
    exclude("**/*.map")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("logica-yandex.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    // A task-local reference keeps the actions free of script objects for the configuration cache.
    val distribution = compatibilityDistribution
    doFirst {
        val distributionRoot = distribution.get().asFile
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
            "word/v3/answers.txt",
            "word/v4/answers.txt",
            "word/v5/answers.txt",
            "levels/v1/word_ru/easy.lvp",
            "levels/v1/word_en/easy.lvp",
            "levels/v1/word_tr/easy.lvp",
            "levels/v2/checksums.sha256",
            "levels/v2/nonogram/easy.lvp",
            "nonogram/v3/easy.txt",
            // The game sounds, fetched relative to the page like everything above.
            "composeResources/com.stanisryz.logica.shared.ui.generated.resources/files/sounds/tap.wav",
        ).forEach { path ->
            check(distributionRoot.resolve(path).isFile) {
                "The Yandex distribution is missing canonical asset $path."
            }
        }
    }

    doLast {
        val distributionRoot = distribution.get().asFile
        val uncompressedBytes = distributionRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val archive = archiveFile.get().asFile
        logger.lifecycle(
            "Yandex distribution: $uncompressedBytes bytes uncompressed, " +
                "${archive.length()} bytes compressed at ${archive.absolutePath}",
        )
    }
}

compose.resources {
    packageOfResClass = "com.stanisryz.logica.web.generated.resources"
    generateResClass = always
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("generated") }
    }
}
