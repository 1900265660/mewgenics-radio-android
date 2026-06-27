plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val radioManifestUrl = providers.gradleProperty("RADIO_MANIFEST_URL")
    .orElse(providers.environmentVariable("RADIO_MANIFEST_URL"))
    .orElse("")
    .get()
val allowCleartextRelease = providers.gradleProperty("ALLOW_CLEARTEXT")
    .orElse(providers.environmentVariable("ALLOW_CLEARTEXT"))
    .orElse("false")
    .get()

android {
    namespace = "com.local.mewgenicsradio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.mewgenicsradio"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-private"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "RADIO_MANIFEST_URL", "\"$radioManifestUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        create("onlineDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".online"
            versionNameSuffix = "-online"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "RADIO_MANIFEST_URL", "\"$radioManifestUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "RADIO_MANIFEST_URL", "\"$radioManifestUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = allowCleartextRelease
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
