plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.mehrdad_abdi.qintar"
    compileSdk = 36

    defaultConfig {
        // Kept from v1 so that v2 installs as an update and can migrate v1 data.
        applicationId = "io.github.mehrdad_abdi.quranbookmarks"
        minSdk = 26
        targetSdk = 36
        versionCode = 201
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key comes from the environment (CI secrets, see docs/v2/RELEASING.md).
    // Without it the release build is left unsigned so that it can still be compiled and checked.
    val keystoreFile = System.getenv("QINTAR_KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("QINTAR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QINTAR_KEY_ALIAS")
                keyPassword = System.getenv("QINTAR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to an installed release (and next to v1).
            // `-Pqintar.v1UpgradeTest=true` drops the suffix, so that a debug build installs over a
            // debug build of v1 (same debug key) to test the in-place upgrade (docs/v2/RELEASING.md).
            if (providers.gradleProperty("qintar.v1UpgradeTest").orNull != "true") {
                applicationIdSuffix = ".debug"
            }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { generateLocaleConfig = false }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.reorderable)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
