plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.drdisplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.drdisplay"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // Material3
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    //androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Google Play Services (GPS)
    implementation("com.google.android.gms:play-services-location:21.4.0")
    // OkHttp (network requests)
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    // OSMdroid (map rendering)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Jetpack Compose icons
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Compose BOM (keeps versions consistent)
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    // Gson
    implementation("com.google.code.gson:gson:2.14.0")
    // Coroutines (needed for .first() and launch)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.compose.foundation:foundation:1.12.0") // or newer

}