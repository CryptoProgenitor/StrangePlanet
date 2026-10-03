plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Shareable builds carry only the 64-bit ARM copy of the native code (all current
// phones). The online-Pong WebRTC library otherwise ships for 4 CPU types — ~43 MB of
// a ~55 MB APK. Pass -PallAbis to keep the x86 copies for emulator testing.
fun com.android.build.api.dsl.ApplicationBuildType.phoneAbiOnly() {
    if (!project.hasProperty("allAbis")) ndk { abiFilters += "arm64-v8a" }
}

android {
    namespace = "com.quokkalabs.strangeplanet"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.quokkalabs.strangeplanet"
        minSdk = 26
        targetSdk = 36
        versionCode = 70
        versionName = "3.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            phoneAbiOnly()
        }
        // Release-speed build signed with the local debug key, so it installs over the
        // debug app (keeping high scores). Debug builds are debuggable, which makes
        // Compose much slower — measure jank on this one: .\gradlew.bat installProfile
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            phoneAbiOnly()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.webrtc.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}