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
    description = "Runs the opt-in Word lexicon and deterministic generator quality gate."
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
