import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val source = rootProject.file("source")
val nativeFolder: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val system = when {
        "win" in os -> "windows"
        "mac" in os || "darwin" in os -> "macos"
        else -> "linux"
    }
    val cpu = when (arch) {
        "amd64", "x86_64", "x64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> arch
    }
    "$system-$cpu"
}
val nativeBuild = layout.buildDirectory.dir("native-build")
val nativeResources = layout.buildDirectory.dir("native-resources")
val nativeOutput = layout.buildDirectory.dir("native-resources/native/$nativeFolder")
val cmakeExecutable: String? = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    .map { File(it, if (File.separatorChar == '\\') "cmake.exe" else "cmake") }
    .firstOrNull { it.isFile }?.absolutePath

val configureNativeAudio by tasks.registering(Exec::class) {
    val cmake = cmakeExecutable
    val buildDir = nativeBuild.get().asFile
    val outDir = nativeOutput.get().asFile
    val javaHome = System.getProperty("java.home")
    val apple = "mac" in System.getProperty("os.name").lowercase()
    onlyIf { cmake != null }
    inputs.dir(File(source, "native"))
    outputs.file(File(buildDir, "CMakeCache.txt"))
    isIgnoreExitValue = true
    commandLine(
        listOfNotNull(
            cmake ?: "cmake",
            "-S", File(source, "native").absolutePath,
            "-B", buildDir.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DVCA_OUTPUT_DIR=${outDir.absolutePath.replace('\\', '/')}",
            "-DVCA_JAVA_HOME=${javaHome.replace('\\', '/')}",
            if (apple) "-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0" else null
        )
    )
}

val buildNativeAudio by tasks.registering(Exec::class) {
    dependsOn(configureNativeAudio)
    val cmake = cmakeExecutable
    val buildDir = nativeBuild.get().asFile
    onlyIf { cmake != null && File(buildDir, "CMakeCache.txt").isFile }
    inputs.dir(File(source, "native"))
    outputs.dir(nativeOutput)
    isIgnoreExitValue = true
    commandLine(cmake ?: "cmake", "--build", buildDir.absolutePath, "--config", "Release", "--target", "vconverter_audio", "vconverter_touch")
}

tasks.named("processResources") {
    dependsOn(buildNativeAudio)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets.named("main") {
        kotlin.setSrcDirs(listOf(File(source, "v-converterui"), File(source, "v-touch"), File(source, "shared"), File(source, "jvm"), File(source, "desktop")))
        resources.setSrcDirs(listOf(rootProject.file("assets"), nativeResources))
        resources.exclude("icons/desktop/**", "**/README.txt")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.sevenzip.desktop)
    implementation(libs.sevenzip.desktop.platforms)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.vorbisspi)
    implementation(libs.mp3spi)
}

compose.desktop {
    application {
        mainClass = "com.vconvertercrew.vconverter.desktop.MainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "V-Converter"
            packageVersion = "0.1.0"
            description = "Friday Night Funkin' converter for Psych Engine, Codename Engine and V-Slice"
            vendor = "VConverterCrew"
            modules("java.naming", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(rootProject.file("assets/icons/desktop/icon.ico"))
                menuGroup = "V-Converter"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f3e2d9a-4b1c-4e8a-9d5f-2a7b8c1e0f43"
            }
            macOS {
                iconFile.set(rootProject.file("assets/icons/desktop/icon.icns"))
                bundleID = "com.vconvertercrew.vconverter"
                packageName = "V-Converter"
                dockName = "V-Converter"
                packageVersion = "1.0.0"
                dmgPackageVersion = "1.0.0"
                appCategory = "public.app-category.developer-tools"
            }
            linux {
                iconFile.set(rootProject.file("assets/icons/desktop/icon.png"))
                packageName = "v-converter"
                debMaintainer = "brenoamonteiro7@gmail.com"
                menuGroup = "Development"
                appCategory = "Development"
                shortcut = true
            }
        }
    }
}
