plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.noircinema.rshgbt"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "2.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val syncInfuseApks by tasks.registering {
    group = "build"
    description = "Syncs the release APK exclusively to app-release.apk at the project root and removes duplicate APK files."
    outputs.upToDateWhen { false }

    doLast {
        val rootDir = rootProject.projectDir
        val targetApk = rootDir.resolve("app-release.apk")
        val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (releaseApk.exists()) {
            releaseApk.copyTo(targetApk, overwrite = true)
        }

        val obsoleteApks = listOf(
            rootDir.resolve("infuse.apk"),
            rootDir.resolve("Infuse.apk"),
            rootDir.resolve("infuse .apk"),
            rootDir.resolve("app-debug.apk"),
            rootDir.resolve("public/infuse.apk"),
            rootDir.resolve("public/Infuse.apk"),
            rootDir.resolve("public/apk/infuse.apk"),
            rootDir.resolve("public/apk/Infuse.apk"),
            rootDir.resolve("public/infuse-latest.apk"),
            rootDir.resolve("release/infuse.apk"),
            rootDir.resolve("release/Infuse.apk"),
            rootDir.resolve("release/Infuse-latest.apk"),
            rootDir.resolve("release/app-debug.apk"),
            rootDir.resolve("release/app-release.apk"),
            rootDir.resolve(".build-outputs/infuse.apk"),
            rootDir.resolve(".build-outputs/Infuse.apk"),
            rootDir.resolve(".build-outputs/Infuse-latest.apk"),
            rootDir.resolve(".build-outputs/app-debug.apk"),
            rootDir.resolve(".build-outputs/app-release.apk")
        )
        obsoleteApks.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
    finalizedBy(syncInfuseApks)
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy(syncInfuseApks)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


