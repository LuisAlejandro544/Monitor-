import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Configuración Gradle del módulo :core:ui.
 * Define el sistema de diseño Material 3 (paleta estilo Administrador de Tareas de escritorio),
 * iconografía Material Icons Extended, Activity Compose (launchers de permisos) y Canvas.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.memscope.taskmonitor.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
}
