import java.util.Properties

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "se.cravity.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "se.cravity.app"
        minSdk = 26
        targetSdk = 35
        // GITHUB_RUN_NUMBER strictly increases on every Actions run, so a CI-built AAB always
        // carries a versionCode Google Play hasn't seen before. Falls back to 1 for local builds.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    // Release signing: CI reads the upload key from env vars (see android-build.yml),
    // local builds from local.properties. Neither the keystore nor its passwords are committed.
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("ANDROID_KEYSTORE_PATH")
                ?: localProps.getProperty("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                    ?: localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                    ?: localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                    ?: localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.getByName("release").let {
                if (it.storeFile != null) signingConfig = it
            }
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
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
