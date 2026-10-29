import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val shared = "../app/src/main/java"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets.named("main") {
        kotlin.srcDir(shared)
        kotlin.exclude(
            "com/brenninhotools/vconverter/ui/**",
            "com/brenninhotools/vconverter/MainActivity.kt",
            "com/brenninhotools/vconverter/io/DocumentIO.kt",
            "com/brenninhotools/vconverter/io/Downloads.kt",
            "com/brenninhotools/vconverter/io/Importer.kt",
            "com/brenninhotools/vconverter/io/TreeStorage.kt"
        )
        resources.srcDir("../app/src/main/resources")
        resources.srcDir("../assets")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.org.json)
    implementation(libs.sevenzip.desktop)
    implementation(libs.sevenzip.desktop.platforms)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.vorbisspi)
    implementation(libs.mp3spi)
}

compose.desktop {
    application {
        mainClass = "com.brenninhotools.vconverter.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "V-Converter"
            packageVersion = "0.0.1"
            description = "Friday Night Funkin' converter for Psych Engine, Codename Engine and V-Slice"
            vendor = "BrenninhoTools"
            modules("java.naming", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "V-Converter"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f3e2d9a-4b1c-4e8a-9d5f-2a7b8c1e0f43"
            }
        }
    }
}
