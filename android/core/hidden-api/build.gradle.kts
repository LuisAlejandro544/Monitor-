/**
 * Configuración Gradle del módulo :core:hidden-api.
 * Provee stubs de compilación para interfaces ocultas del framework Android
 * (IPackageManager, IPermissionManager, IActivityManager) junto con soporte para
 * HiddenApiRefinePlugin (dev.rikka.tools.refine).
 * Se consume como compileOnly en :core:shizuku para no duplicar clases del framework en el APK.
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.memscope.taskmonitor.core.hiddenapi"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    annotationProcessor(libs.refine.annotation.processor)
    compileOnly(libs.refine.annotation)
}
