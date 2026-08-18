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

    jvmToolchain(17)

    sourceSets {
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

    doFirst {
        val requestedSeedCount = balanceSeedCount.get()
        require(requestedSeedCount.toIntOrNull()?.let { it > 0 } == true) {
            "-PbalanceSeeds must be a positive integer."
        }
        args(requestedSeedCount)
    }
}

tasks.register<JavaExec>("crownsQualityCheck") {
    group = "verification"
    description = "Runs the opt-in deterministic Crowns generator quality sweep."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.crowns.quality.CrownsQualityRunner")

    doFirst {
        val requestedSeedCount = crownsSeedCount.get()
        require(requestedSeedCount.toIntOrNull()?.let { it > 0 } == true) {
            "-PcrownsSeeds must be a positive integer."
        }
        args(requestedSeedCount)
    }
}

tasks.register<JavaExec>("wordQualityCheck") {
    group = "verification"
    description = "Runs the opt-in Word V1 compatibility and V2 lexicon/generator quality gate."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.word.quality.WordQualityRunner")

    doFirst {
        val requestedSeedCount = wordSeedCount.get()
        require(requestedSeedCount.toIntOrNull()?.let { it > 0 } == true) {
            "-PwordSeeds must be a positive integer."
        }
        args(requestedSeedCount)
    }
}

/**
 * Developer-only offline freeze of the Catalog Level Packs. It is never part of a normal build and
 * never runs on a device: it writes the compact bucket assets the application reads read-only.
 */
tasks.register<JavaExec>("buildCatalogLevelPacks") {
    group = "build"
    description = "Regenerates candidates and verifies they match the frozen Catalog Level Pack V1 assets."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.catalog.quality.CatalogLevelPackBuilder")
    maxHeapSize = "2g"

    doFirst {
        args(
            rootProject.layout.projectDirectory
                .dir("app/src/main/assets")
                .asFile.path,
            providers.gradleProperty("levelPackGames").orElse("all").get(),
            providers.gradleProperty("levelPackSlots").orElse("10000").get(),
        )
    }
}

tasks.register<JavaExec>("verifyCatalogLevelPacks") {
    group = "verification"
    description = "Verifies SHA-256 checksums of the frozen Catalog Level Pack V1 assets."
    dependsOn(qualityCompilation.compileAllTaskName)
    classpath(qualityCompilation.output.allOutputs, qualityCompilation.runtimeDependencyFiles)
    mainClass.set("com.stanisryz.logica.puzzle.core.catalog.quality.CatalogLevelPackIntegrity")
    args(
        rootProject.layout.projectDirectory
            .dir("app/src/main/assets")
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
