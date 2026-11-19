plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val source = rootProject.file("source")

val vtouchSource = File(source, "native/v-touch")

fun vtouchLibrary(target: String): File = layout.buildDirectory.dir("vtouch/$target").get().asFile

val vtouchTasks = mapOf(
    "iosArm64" to ("iphoneos" to "arm64-apple-ios14.0"),
    "iosSimulatorArm64" to ("iphonesimulator" to "arm64-apple-ios14.0-simulator")
).mapValues { (target, platform) ->
    tasks.register<Exec>("buildVTouch${target.replaceFirstChar { it.uppercase() }}") {
        val out = vtouchLibrary(target)
        inputs.dir(vtouchSource)
        outputs.file(File(out, "libvtouch.a"))
        commandLine(
            "sh", "-c",
            "mkdir -p '${out.absolutePath}' && " +
                "xcrun -sdk ${platform.first} clang++ -std=c++17 -O2 -fno-exceptions -fno-rtti -target ${platform.second} " +
                "-I '${File(vtouchSource, "include").absolutePath}' -c '${File(vtouchSource, "src/VTouchEngine.cpp").absolutePath}' " +
                "-o '${out.absolutePath}/VTouchEngine.o' && " +
                "rm -f '${out.absolutePath}/libvtouch.a' && " +
                "xcrun -sdk ${platform.first} ar rcs '${out.absolutePath}/libvtouch.a' '${out.absolutePath}/VTouchEngine.o'"
        )
    }
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "VConverter"
            isStatic = true
        }
        target.compilations.getByName("main").cinterops.create("stbvorbis") {
            defFile(File(source, "native/cinterop/stbvorbis.def"))
            includeDirs(File(source, "native/cinterop"), File(source, "native/v-miniaudio/src/external"))
        }
        target.compilations.getByName("main").cinterops.create("vminiaudio") {
            defFile(File(source, "native/cinterop/vminiaudio.def"))
            includeDirs(File(source, "native/v-miniaudio/include"), File(source, "native/v-miniaudio/src"))
        }
        val vtouchInterop = target.compilations.getByName("main").cinterops.create("vtouch") {
            defFile(File(source, "native/cinterop/vtouch.def"))
            includeDirs(File(vtouchSource, "include"))
            extraOpts("-libraryPath", vtouchLibrary(target.name).absolutePath)
        }
        tasks.named(vtouchInterop.interopProcessingTaskName) {
            dependsOn(vtouchTasks.getValue(target.name))
        }
    }

    sourceSets {
        iosMain {
            kotlin.setSrcDirs(listOf(File(source, "v-converterui"), File(source, "v-touch"), File(source, "shared"), File(source, "portable"), File(source, "ios")))
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
