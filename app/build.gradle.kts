plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStorePath = System.getenv("AURA_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("AURA_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("AURA_KEY_ALIAS")
val releaseKeyPassword = System.getenv("AURA_KEY_PASSWORD")
val hasPrivateReleaseSigning = listOf(
    releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.killswitch.aura"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.killswitch.aura"
        minSdk = 26
        targetSdk = 36
        // CI numbers every build; a local build falls back to 100, which still beats the old 6.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = 100 + (run ?: 0)
        versionName = "3.1." + (run ?: "dev")
        manifestPlaceholders["appLabel"] = "Aura Alarm"
    }

    buildTypes {
        // Release APKs use the private key supplied through environment variables / GitHub Actions secrets.
        // Without it, local assembleRelease output is unsigned and must not be distributed.
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasPrivateReleaseSigning) signingConfigs.getByName("privateRelease") else null
        }
    }

    signingConfigs {
        if (hasPrivateReleaseSigning) {
            create("privateRelease") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += arrayOf("DebugProbesKt.bin", "kotlin-tooling-metadata.json", "kotlin/**", "META-INF/*.version", "META-INF/**/LICENSE.txt")
        }
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.12.0")
    implementation("org.jetbrains.compose.material:material-ripple:1.12.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
