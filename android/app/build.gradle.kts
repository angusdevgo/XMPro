plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release builds must be signed with the project keystore; debug signing is
// never reused for a release APK.
val signingValues = mapOf(
    "storeFile" to System.getenv("ANDROID_KEYSTORE_PATH"),
    "storePassword" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    "keyAlias" to System.getenv("ANDROID_KEY_ALIAS"),
    "keyPassword" to System.getenv("ANDROID_KEY_PASSWORD"),
)
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
if (releaseRequested) {
    require(signingValues.values.all { !it.isNullOrBlank() }) {
        "Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD; debug signing is never used for release."
    }
    require(file(signingValues.getValue("storeFile")!!).isFile) { "Release keystore does not exist" }
}

android {
    namespace = "teikengstudio.xmpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "teikengstudio.xmpro"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "1.2.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        resourceConfigurations += listOf("zh", "en")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // The bundled FFmpeg/FFprobe wrappers are executed as separate
            // processes, so they must stay on the filesystem.
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libxmplus_ffmpeg.so", "**/libxmplus_ffprobe.so")
        }
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    signingConfigs {
        if (signingValues.values.all { !it.isNullOrBlank() }) {
            create("release") {
                storeFile = file(signingValues.getValue("storeFile")!!)
                storePassword = signingValues.getValue("storePassword")
                keyAlias = signingValues.getValue("keyAlias")
                keyPassword = signingValues.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
    // Android supplies org.json at runtime; unit tests need a JVM implementation.
    testImplementation("org.json:json:20240303")
}

// Native media tools are built by tool/build_android_media.sh. Local debug
// builds may proceed without them, but release/CI builds must ship them.
val verifyAndroidMediaTools = tasks.register("verifyAndroidMediaTools") {
    val required = providers.gradleProperty("xmpro.requireNativeTools")
        .map { it.toBoolean() }
        .orElse(false)
    val abiDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
    doLast {
        val missing = listOf("libxmplus_ffmpeg.so", "libxmplus_ffprobe.so").filter {
            !abiDir.file(it).asFile.let { file -> file.isFile && file.length() > 0 }
        }
        if (missing.isEmpty()) return@doLast
        val message = "Missing arm64-v8a media tools ${missing.joinToString()}; " +
            "run 'bash tool/build_android_media.sh arm64-v8a'."
        if (required.get()) throw GradleException(message) else logger.warn("WARNING: $message")
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyAndroidMediaTools) }
