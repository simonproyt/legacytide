plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.simonproyt.legacytide"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.simonproyt.legacytide"
        minSdk = 18
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      buildConfig = true
      aidl = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.media:media:1.6.0")
  implementation("com.squareup.picasso:picasso:2.5.2")
  implementation("androidx.multidex:multidex:2.0.1")
  
  // ExoPlayer for Audio Playback
  implementation("com.google.android.exoplayer:exoplayer-core:2.18.1")
  implementation("com.google.android.exoplayer:exoplayer-dash:2.18.1")
  implementation("com.google.android.exoplayer:extension-okhttp:2.18.1")
  implementation(libs.okhttp3) {
      version { strictly("3.12.13") }
  }
  implementation(libs.okhttp3.logging) {
      version { strictly("3.12.13") }
  }
  implementation(libs.gson)
  implementation("org.conscrypt:conscrypt-android:2.5.2")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
