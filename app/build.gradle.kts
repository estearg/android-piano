import java.util.Properties

plugins {
    id("com.android.application")
}

// The upload key lives OUTSIDE the repository (never commit it). Override the folder with
// -PkeystoreDir=/other/path or the KEYSTORE_DIR environment variable.
val keystoreDir = (findProperty("keystoreDir") as String?) ?: System.getenv("KEYSTORE_DIR") ?: "/home/saw/piano-jks"
val keystoreFile = file("$keystoreDir/harika-piyano.jks")
val passwordFile = file("$keystoreDir/sifre.txt")

android {
    namespace = "org.esteban.piano"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.esteban.piano"
        // Google Mobile Ads (play-services-ads) needs Android 6.0 or newer
        minSdk = 23
        targetSdk = 36
        versionCode = 14
        versionName = "1.11"
    }

    buildFeatures {
        resValues = true
    }

    signingConfigs {
        if (keystoreFile.exists() && passwordFile.exists()) {
            create("release") {
                val password = passwordFile.readText().trim()
                storeFile = keystoreFile
                storePassword = password
                keyAlias = "harika-piyano"
                keyPassword = password
            }
        }
    }

    buildTypes {
        debug {
            // Google's public test banner: never load real ads while developing
            resValue("string", "admob_banner_unit_id", "ca-app-pub-3940256099942544/6300978111")
        }
        release {
            resValue("string", "admob_banner_unit_id", "ca-app-pub-7884616703573105/2275754980")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // SoundPool opens the sounds as file descriptors, which needs them stored uncompressed
    androidResources {
        noCompress += "wav"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-ads:25.4.0")
}
