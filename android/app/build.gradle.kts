import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Configuración Gradle del módulo principal :app (MemScope).
 * Configura minSdk 26 (Android 8.0+), compileSdk 36 / targetSdk 35, soporte explícito de
 * arquitecturas de 32 y 64 bits (armeabi-v7a, arm64-v8a, x86, x86_64) y ensambla todos los módulos.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.refine)
}

android {
    namespace = "com.memscope.taskmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.memscope.taskmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Soporte completo para dispositivos de 32 bits (armeabi-v7a / x86)
        // y 64 bits (arm64-v8a / x86_64) en APK universal para distribución directa.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:shizuku"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))

    implementation(project(":feature:monitor"))
    implementation(project(":feature:peaks"))
    implementation(project(":feature:freezer"))
    implementation(project(":feature:shizuku_tools"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hiddenapibypass)
    implementation(libs.refine.runtime)

    testImplementation(libs.junit)
}
