/** The official Yandex rewarded demo unit; it always fills and never bills a real placement. */
val demoRewardedAdUnitId = "demo-rewarded-yandex"

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
    }

    buildTypes {
        debug {
            // Development and local testing never touch the production placement.
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$demoRewardedAdUnitId\"")
        }
        release {
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$releaseRewardedAdUnitId\"")
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
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":puzzle-core"))

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

    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
