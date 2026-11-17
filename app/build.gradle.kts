import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.docbobert6.auraweather"
    minSdk = 24
    targetSdk = 36
    versionCode = 12
    versionName = "0.3.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // ABI splits: the release ships the arm64-only APK (never the ~66 MB
  // universal). The debug APK is split the same way, so its output is
  // app-arm64-v8a-debug.apk (see .github/workflows/ci.yml).
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a")
      isUniversalApk = false
    }
  }

  // Release signing uses the app's own keystore. Local builds keep the
  // file-based keystore outside the repo (aura-weather/keystore/, never
  // committed) with its passwords in keystore.properties. CI tag runs
  // instead take the keystore from the AURA_KEYSTORE_* repo secrets
  // (see .github/workflows/release.yml): the keystore is decoded from
  // AURA_KEYSTORE_BASE64 and the path + passwords arrive via env, so
  // no secret ever lives in the repo. Branch CI builds have neither,
  // so their release APK stays unsigned (the R8 check).
  signingConfigs {
    create("release") {
      val envPath = System.getenv("AURA_KEYSTORE_PATH")
      val envStorePassword = System.getenv("AURA_KEYSTORE_PASSWORD")
      val envAlias = System.getenv("AURA_KEY_ALIAS")
      val envKeyPassword = System.getenv("AURA_KEY_PASSWORD")
      if (envPath != null && envStorePassword != null && envAlias != null && envKeyPassword != null) {
        storeFile = file(envPath)
        storePassword = envStorePassword
        keyAlias = envAlias
        keyPassword = envKeyPassword
      } else {
        val keystoreFile = file("${rootDir}/../keystore/auraweather-release.jks")
        val propsFile = file("${rootDir}/../keystore/keystore.properties")
        if (keystoreFile.exists() && propsFile.exists()) {
          val props = Properties().apply { load(propsFile.inputStream()) }
          storeFile = keystoreFile
          storePassword = props.getProperty("storePassword")
          keyAlias = props.getProperty("keyAlias")
          keyPassword = props.getProperty("keyPassword")
        }
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Signed when the app's own keystore exists (local release
      // builds) or the CI tag run supplied one via env; otherwise
      // the release APK is unsigned (branch CI: the R8 check).
      if (file("${rootDir}/../keystore/auraweather-release.jks").exists() || System.getenv("AURA_KEYSTORE_PATH") != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  // Phase 1 radar rebuild: MapLibre Native 13.7.1 direct (Vulkan
  // single-backend — NEVER the dual-backend AAR). NOT the Compose
  // wrapper (it cannot set volatile/prefetch in its released source).
  implementation(libs.maplibre.android.sdk)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
