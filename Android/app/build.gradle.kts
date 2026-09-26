plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.poldivers.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.poldivers.app"
        minSdk = 26
        targetSdk = 35
        // CI passes -PversionCode=<n>; it must grow with every release for in-app updates.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0-dev"

        // Phones only: skip x86 native libs (ML Kit translation ships them for every ABI).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Release builds are signed with the key from CI secrets (see .github/workflows/android.yml).
    // Every update must carry the same signature, otherwise Android refuses to install it over
    // the existing app.
    val keystorePath = System.getenv("POLDIVERS_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("POLDIVERS_KEYSTORE_PASSWORD")
                keyAlias = "poldivers"
                keyPassword = System.getenv("POLDIVERS_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)

    // On-device English -> Polish translation for wiki / static English texts ("Przetłumacz").
    implementation(libs.mlkit.translate)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
