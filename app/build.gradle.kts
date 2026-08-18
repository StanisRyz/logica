/** The official Yandex rewarded demo unit; it always fills and never bills a real placement. */
val demoRewardedAdUnitId = "demo-rewarded-yandex"

/** The official Yandex interstitial demo unit. Same idea, different format and different placement. */
val demoInterstitialAdUnitId = "demo-interstitial-yandex"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ktlint)
}

/**
 * The Yandex rewarded placement for release builds. It is an ad unit identifier, not a credential,
 * but it is still never committed: set `logica.rewardedAdUnitId` in `local.properties`, in a private
 * `gradle.properties`, or as `ORG_GRADLE_PROJECT_logica.rewardedAdUnitId` on the build machine. When
 * it is missing the release build falls back to the demo unit, which is loud in the build log and
 * safe, instead of silently shipping someone else's placement.
 */
val releaseRewardedAdUnitId: String =
    (providers.gradleProperty("logica.rewardedAdUnitId").orNull ?: demoRewardedAdUnitId).also {
        if (it == demoRewardedAdUnitId) {
            logger.warn("logica.rewardedAdUnitId is not set: release builds would use the Yandex demo rewarded unit.")
        }
    }

/**
 * The Yandex interstitial placement for release builds. It is a separate placement from the rewarded
 * one and is configured separately: set `logica.interstitialAdUnitId` in `local.properties`, in a
 * private `gradle.properties`, or as `ORG_GRADLE_PROJECT_logica.interstitialAdUnitId`. A missing
 * property falls back to the demo unit loudly rather than shipping an unrelated placement, exactly
 * like the rewarded configuration above.
 */
val releaseInterstitialAdUnitId: String =
    (providers.gradleProperty("logica.interstitialAdUnitId").orNull ?: demoInterstitialAdUnitId).also {
        if (it == demoInterstitialAdUnitId) {
            logger.warn("logica.interstitialAdUnitId is not set: release builds would use the Yandex demo interstitial unit.")
        }
    }

/**
 * The RuStore Console application ID the Pay SDK initializes from. It is per-developer configuration
 * rather than committed source: set `logica.rustoreConsoleAppId` in `local.properties`, in a private
 * `gradle.properties`, or as `ORG_GRADLE_PROJECT_logica.rustoreConsoleAppId`. Leaving it unset is a
 * supported build: the Pay SDK simply never becomes usable and the Gem Store reports itself
 * unavailable, which changes nothing else in the application.
 */
val rustoreConsoleAppId: String =
    (providers.gradleProperty("logica.rustoreConsoleAppId").orNull ?: "").also {
        if (it.isEmpty()) logger.warn("logica.rustoreConsoleAppId is not set: RuStore purchases will be unavailable.")
    }

/** The deeplink scheme RuStore returns to after payment. A public identifier, not a credential. */
val rustorePayScheme = "logicapay"

android {
    namespace = "com.stanisryz.logica"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stanisryz.logica"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Read by the Pay SDK's own ContentProvider; see the manifest for what each one does.
        manifestPlaceholders["rustoreConsoleAppId"] = rustoreConsoleAppId
        manifestPlaceholders["rustorePayScheme"] = rustorePayScheme
        // The same ID the SDK initializes from, readable by the application itself: the Store decides
        // it is unconfigured from the build rather than from a RuStore call that has already failed.
        buildConfigField("String", "RUSTORE_CONSOLE_APP_ID", "\"$rustoreConsoleAppId\"")
    }

    buildTypes {
        debug {
            // Development and local testing never touch the production placements.
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$demoRewardedAdUnitId\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"$demoInterstitialAdUnitId\"")
        }
        release {
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$releaseRewardedAdUnitId\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"$releaseInterstitialAdUnitId\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").assets.srcDir(rootProject.file("puzzle-data"))
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":platform-contracts"))
    implementation(project(":puzzle-core"))
    implementation(project(":shared-ui"))

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.yandex.mobileads)
    implementation(platform(libs.rustore.bom))
    implementation(libs.rustore.pay)

    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
