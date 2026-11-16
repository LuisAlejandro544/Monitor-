/**
 * Configuración raíz de módulos Gradle para MemScope.
 * Arquitectura modular dividida en capa core (modelos, stubs ocultos, puente Shizuku,
 * persistencia/datos, diseño UI) y capa feature (pantallas independientes).
 */
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MemScope"

include(":app")
include(":core:model")
include(":core:hidden-api")
include(":core:shizuku")
include(":core:data")
include(":core:ui")
include(":feature:monitor")
include(":feature:peaks")
include(":feature:freezer")
include(":feature:shizuku_tools")
