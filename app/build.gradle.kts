plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Missing all signing variables permits an unsigned local build. Partial configuration
// fails explicitly rather than silently producing an unsigned release.
val signingNames = listOf("SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
val signingValues = signingNames.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingValues.values.any { !it.isNullOrBlank() }
if (hasReleaseSigning) {
    val missing = signingNames.filter { signingValues[it].isNullOrBlank() }
    require(missing.isEmpty()) { "Incomplete release signing: missing ${missing.joinToString()}" }
}

android {
    namespace = "com.example.yhviewer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.zer0rin.yhviewer"
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "network"
    productFlavors {
        create("strict") {
            dimension = "network"
            buildConfigField("boolean", "NETWORK_CAPABLE", "false")
            buildConfigField("boolean", "PYTHON_RUNTIME", "false")
        }
        create("standard") {
            dimension = "network"
            isDefault = true
            buildConfigField("boolean", "NETWORK_CAPABLE", "true")
            buildConfigField("boolean", "PYTHON_RUNTIME", "true")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingValues.getValue("SIGNING_STORE_FILE")!!)
                storePassword = signingValues.getValue("SIGNING_STORE_PASSWORD")
                keyAlias = signingValues.getValue("SIGNING_KEY_ALIAS")
                keyPassword = signingValues.getValue("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                // Optimize the full program: partial packageScope repackaged a Kotlin
                // subclass away from its package-private superclass and crashed startup.
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    // Room's migration helper needs serialization 1.8.1. Align the app and test APKs:
    // otherwise main's transitive core 1.7.3 overrides the test runtime and causes AbstractMethodError.
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}