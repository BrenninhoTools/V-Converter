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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.omicronapps") }
        }
    }
}

rootProject.name = "V-Converter"
val modules = providers.gradleProperty("vconverter.modules").orNull?.split(',')?.map { it.trim() }?.toSet()
fun wanted(name: String) = modules == null || name in modules
if (wanted("app")) include(":app")
if (wanted("desktop")) include(":desktop")
if (wanted("ios") && System.getProperty("os.name").startsWith("Mac")) include(":ios")
