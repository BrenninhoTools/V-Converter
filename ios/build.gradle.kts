plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val source = rootProject.file("source")

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "VConverter"
            isStatic = true
        }
        target.compilations.getByName("main").cinterops.create("stbvorbis") {
            defFile(File(source, "native/cinterop/stbvorbis.def"))
            includeDirs(File(source, "native/cinterop"))
        }
        target.compilations.getByName("main").cinterops.create("vminiaudio") {
            defFile(File(source, "native/cinterop/vminiaudio.def"))
            includeDirs(File(source, "native/v-miniaudio/include"), File(source, "native/v-miniaudio/src"))
        }
    }

    sourceSets {
        iosMain {
            kotlin.setSrcDirs(listOf(File(source, "shared"), File(source, "ios")))
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
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        optIn.add("kotlinx.cinterop.BetaInteropApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
