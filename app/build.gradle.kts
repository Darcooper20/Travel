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
        // Uses a fixed, checked-in debug keystore (app/debug.keystore)
        // instead of AGP's auto-generated one. This matters a lot for CI:
        // a debug keystore auto-created on an ephemeral GitHub Actions
        // runner would be a brand-new random key on every single build, so
        // every APK from a fresh runner would have a different signature -
        // Android refuses to install an "update" whose signature doesn't
        // match what's already installed ("App not installed"). A debug
        // keystore isn't sensitive (it's not used to prove anything, and
        // Android's own tooling uses the fixed password "android" by
        // convention), so committing it is the standard fix for
        // reproducible CI debug builds. For a signature that's yours alone
        // (recommended once you're using this daily), generate your own
        // release keystore and point a "release" signing config at it
        // instead - see README.md.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
