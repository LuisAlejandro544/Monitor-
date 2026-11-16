/**
 * Archivo de compilación raíz (Top-level build file).
 * Declara los plugins de Gradle que aplicarán los submódulos sin instanciarlos en la raíz.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.refine) apply false
}
