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
