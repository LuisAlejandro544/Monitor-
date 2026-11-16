import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Configuración Gradle del módulo :core:model.
 * Contiene únicamente modelos de datos puros e inmutables compartidos entre
 * todas las capas (shizuku, data, ui y features) para evitar dependencias circulares.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.memscope.taskmonitor.core.model"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
