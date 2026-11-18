import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val abis = (findProperty("vconverter.abis") as String?)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.vconvertercrew.vconverter"
    compileSdk = 36
    System.getenv("ANDROID_NDK_HOME")?.let { File(it).name }?.takeIf { it.firstOrNull()?.isDigit() == true }?.let { ndkVersion = it }

    defaultConfig {
        applicationId = "com.vconvertercrew.vconverter"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.1.0-playtest"

        ndk {
            abiFilters += abis
        }
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../source/android/AndroidManifest.xml")
            java.setSrcDirs(listOf("../source/v-converterui", "../source/v-touch", "../source/shared", "../source/jvm", "../source/android"))
            res.setSrcDirs(listOf("../source/android/res"))
            assets.setSrcDirs(listOf("../assets"))
            resources.setSrcDirs(emptyList<String>())
        }
        getByName("test") {
            java.setSrcDirs(listOf("../source/test"))
            resources.setSrcDirs(listOf("../source/test/resources", "../assets"))
        }
    }

    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~:!README.txt:!desktop"
    }

    externalNativeBuild {
        cmake {
            path = file("../source/native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = false
        aidl = false
        renderScript = false
        shaders = false
        resValues = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.documentfile)
    implementation(libs.sevenzip.jbinding)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
