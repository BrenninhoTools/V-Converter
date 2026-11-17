import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val source = rootProject.file("source")

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "vconverter"
        browser {
            commonWebpackConfig {
                outputFileName = "vconverter.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            kotlin.setSrcDirs(listOf(File(source, "v-converterui"), File(source, "v-touch"), File(source, "shared"), File(source, "portable"), File(source, "web")))
            resources.setSrcDirs(listOf(project.file("static"), rootProject.file("assets")))
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

tasks.named<ProcessResources>("wasmJsProcessResources") {
    exclude("icons/desktop/**", "**/README.txt")
}
