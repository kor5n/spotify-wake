import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties()
localProperties.load(rootProject.file("local.properties").inputStream())

android {
    namespace = "com.example.spotifywake"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.spotifywake"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["redirectSchemeName"] = "spotifywake"
        manifestPlaceholders["redirectHostName"] = "callback"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProperties["SPOTIFY_CLIENT_ID"]}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${localProperties["SPOTIFY_CLIENT_SECRET"]}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}