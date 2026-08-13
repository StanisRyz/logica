plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(17)
}

val mainSourceSet = sourceSets.main.get()
val qualitySourceSet =
    sourceSets.create("quality") {
        compileClasspath += mainSourceSet.output
        runtimeClasspath += output + compileClasspath
    }

configurations[qualitySourceSet.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[qualitySourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    testImplementation(libs.junit)
}

val balanceSeedCount = providers.gradleProperty("balanceSeeds").orElse("10")
val crownsSeedCount = providers.gradleProperty("crownsSeeds").orElse("10")
val wordSeedCount = providers.gradleProperty("wordSeeds").orElse("50")

tasks.register<JavaExec>("balanceQualityCheck") {
    group = "verification"
    description = "Runs the opt-in deterministic Balance generator quality sweep."
    dependsOn(qualitySourceSet.classesTaskName)
    classpath = qualitySourceSet.runtimeClasspath
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
    dependsOn(qualitySourceSet.classesTaskName)
    classpath = qualitySourceSet.runtimeClasspath
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
    dependsOn(qualitySourceSet.classesTaskName)
    classpath = qualitySourceSet.runtimeClasspath
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
    dependsOn(qualitySourceSet.classesTaskName)
    classpath = qualitySourceSet.runtimeClasspath
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
    dependsOn(qualitySourceSet.classesTaskName)
    classpath = qualitySourceSet.runtimeClasspath
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
    dependsOn(qualitySourceSet.classesTaskName)
    classpath = qualitySourceSet.runtimeClasspath
    mainClass.set("com.stanisryz.logica.puzzle.core.word.quality.WordLexiconPrepareTool")
    args(
        rootProject.layout.projectDirectory
            .dir("lexicon/word")
            .asFile.path,
        layout.projectDirectory
            .dir("src/main/resources/word/v1")
            .asFile.path,
    )
}
