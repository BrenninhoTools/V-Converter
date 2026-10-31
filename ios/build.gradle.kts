plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val shared = "../app/src/main/java"

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "VConverter"
            isStatic = true
        }
        target.compilations.getByName("main").cinterops.create("stbvorbis") {
            defFile(project.file("src/nativeInterop/cinterop/stbvorbis.def"))
            includeDirs(project.file("src/nativeInterop/cinterop"))
        }
    }

    sourceSets {
        iosMain {
            kotlin.srcDir(shared)
            kotlin.exclude(
                "com/brenninhotools/vconverter/ui/**",
                "com/brenninhotools/vconverter/jvm/**",
                "com/brenninhotools/vconverter/MainActivity.kt",
                "com/brenninhotools/vconverter/io/DocumentIO.kt",
                "com/brenninhotools/vconverter/io/Downloads.kt",
                "com/brenninhotools/vconverter/io/Importer.kt",
                "com/brenninhotools/vconverter/io/TreeStorage.kt"
            )
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
