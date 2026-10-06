import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ktlint)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.stanisryz.logica.puzzle.core"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        val mainCompilation = compilations.getByName("main")
        compilations.create("quality") {
            associateWith(mainCompilation)
        }
    }

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    jvmToolchain(17)

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

val qualityCompilation =
    kotlin.targets
        .getByName("jvm")
        .compilations
        .getByName("quality")

val balanceSeedCount = providers.gradleProperty("balanceSeeds").orElse("10")
val crownsSeedCount = providers.gradleProperty("crownsSeeds").orElse("10")
val wordSeedCount = providers.gradleProperty("wordSeeds").orElse("50")

tasks.register<JavaExec>("balanceQualityCheck") {
    group = "verification"
    description = "Runs the opt-in deterministic Balance generator quality sweep."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.balance.quality.BalanceQualityRunner")

    val requestedSeedCount = balanceSeedCount
    argumentProviders.add(
        CommandLineArgumentProvider {
            val seedCount = requestedSeedCount.get()
            require(seedCount.toIntOrNull()?.let { it > 0 } == true) {
                "-PbalanceSeeds must be a positive integer."
            }
            listOf(seedCount)
        },
    )
}

tasks.register<JavaExec>("crownsQualityCheck") {
    group = "verification"
    description = "Runs the opt-in deterministic Crowns generator quality sweep."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.crowns.quality.CrownsQualityRunner")

    val requestedSeedCount = crownsSeedCount
    argumentProviders.add(
        CommandLineArgumentProvider {
            val seedCount = requestedSeedCount.get()
            require(seedCount.toIntOrNull()?.let { it > 0 } == true) {
                "-PcrownsSeeds must be a positive integer."
            }
            listOf(seedCount)
        },
    )
}

tasks.register<JavaExec>("wordQualityCheck") {
    group = "verification"
    description = "Runs the opt-in Word V1 compatibility and V2 lexicon/generator quality gate."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.word.quality.WordQualityRunner")

    val requestedSeedCount = wordSeedCount
    argumentProviders.add(
        CommandLineArgumentProvider {
            val seedCount = requestedSeedCount.get()
            require(seedCount.toIntOrNull()?.let { it > 0 } == true) {
                "-PwordSeeds must be a positive integer."
            }
            listOf(seedCount)
        },
    )
}

/**
 * Developer-only offline freeze of the Catalog Level Packs. It is never part of a normal build and
 * never runs on a device: it verifies the compact buckets in the canonical shared data corpus.
 */
tasks.register<JavaExec>("buildCatalogLevelPacks") {
    group = "build"
    description = "Regenerates candidates and verifies they match the frozen Catalog Level Pack V1 corpus."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.catalog.quality.CatalogLevelPackBuilder")
    maxHeapSize = "2g"

    val puzzleDataPath =
        rootProject.layout.projectDirectory
            .dir("puzzle-data")
            .asFile.path
    val levelPackGames = providers.gradleProperty("levelPackGames").orElse("all")
    val levelPackSlots = providers.gradleProperty("levelPackSlots").orElse("10000")
    val levelPackCreate = providers.gradleProperty("levelPackCreate").orElse("false")
    argumentProviders.add(
        CommandLineArgumentProvider { listOf(puzzleDataPath, levelPackGames.get(), levelPackSlots.get(), levelPackCreate.get()) },
    )
}

tasks.register<JavaExec>("verifyCatalogLevelPacks") {
    group = "verification"
    description = "Verifies SHA-256 checksums of the frozen Catalog Level Pack V1 corpus."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.catalog.quality.CatalogLevelPackIntegrity")
    args(
        rootProject.layout.projectDirectory
            .dir("puzzle-data")
            .asFile.path,
    )
}

tasks.register<JavaExec>("nonogramLibraryV3Prepare") {
    group = "build"
    description = "Freezes the Nonogram V3 picture library from the candidate library (writes only missing files)."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.nonogram.quality.NonogramLibraryV3Prepare")
    args(
        rootProject.layout.projectDirectory
            .dir("datasets/nonogram/library-v1")
            .asFile.path,
        layout.projectDirectory
            .dir("src/commonMain/resources")
            .asFile.path,
    )
}

tasks.register<JavaExec>("wordLexiconPrepare") {
    group = "build"
    description = "Regenerates the bundled Word V1 lexicon from the curated offline sources."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.word.quality.WordLexiconPrepareTool")
    args(
        rootProject.layout.projectDirectory
            .dir("lexicon/word")
            .asFile.path,
        layout.projectDirectory
            .dir("src/commonMain/resources/word/v1")
            .asFile.path,
    )
}
