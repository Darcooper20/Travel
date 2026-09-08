plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.travelbenefits.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.travelbenefits.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Redirect URI scheme for the Gmail OAuth (AppAuth) flow. This has to
        // be a BUILD-TIME constant, because it's baked into the manifest's
        // intent filter below - it can't be derived from the OAuth client ID
        // you paste into Settings at runtime, since that would need a
        // rebuild to take effect anyway once you finally know it. So: set it
        // via `-PappAuthRedirectScheme=...` (or in gradle.properties) to the
        // "reversed client ID" of your Android OAuth client (see README.md) -
        // e.g. for a client ID "123-abc.apps.googleusercontent.com" that's
        // "com.googleusercontent.apps.123-abc" - then rebuild. GmailAuthManager
        // reads the same value back via BuildConfig so the two can't drift.
        val redirectScheme = (project.findProperty("appAuthRedirectScheme") as String?)
            ?: "com.travelbenefits.app.auth"
        manifestPlaceholders["appAuthRedirectScheme"] = redirectScheme
        buildConfigField("String", "APPAUTH_REDIRECT_SCHEME", "\"$redirectScheme\"")
    }

    signingConfigs {
        // Sideloading note: the debug build is signed with the auto-generated
        // debug keystore, which is enough to install via `adb install` or by
        // opening the APK on-device. To keep a stable signature across
        // reinstalls/updates (recommended once you're using this daily),
        // create your own keystore and point a "release" signing config at
        // it instead - see README.md.
        getByName("debug") {
            // Uses the default debug keystore auto-created by AGP.
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No release signingConfig is set here - see README.md to add your
            // own keystore before running `assembleRelease`. Without one,
            // build and sideload the debug variant.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.appauth)
}
