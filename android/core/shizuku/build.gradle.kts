import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Configuración Gradle del módulo :core:shizuku.
 * Encapsula la integración con Shizuku/Sui, AIDL para el UserService privilegiado,
 * llamadas Binder remotas (IPackageManager / IPermissionManager / IActivityManager)
 * mediante HiddenApiRefinePlugin y lectura /proc del kernel (RAM, picos, CPU e hilos).
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.refine)
}

android {
    namespace = "com.memscope.taskmonitor.core.shizuku"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        aidl = true
        buildConfig = true
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
    compileOnly(project(":core:hidden-api"))

    api(libs.shizuku.api)
    api(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    implementation(libs.refine.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
}
