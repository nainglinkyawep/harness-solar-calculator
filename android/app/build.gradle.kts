import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Release signing is loaded from a local properties file that is NEVER committed.
 *
 * Resolution order:
 * 1. Env HE_KEYSTORE_PROPERTIES (absolute path to keystore.properties)
 * 2. Gradle -Phe.keystore.properties=/path/to/keystore.properties
 * 3. he.keystore.properties entry in local.properties
 * 4. android/keystore.properties (gitignored) next to this module's parent
 *
 * Expected keys: storeFile, storePassword, keyAlias, keyPassword
 * Optional keys: versionCode, versionName, applicationId
 */
fun loadKeystoreProperties(): Properties? {
    val candidates = mutableListOf<File>()

    System.getenv("HE_KEYSTORE_PROPERTIES")?.takeIf { it.isNotBlank() }?.let {
        candidates += file(it)
    }

    (project.findProperty("he.keystore.properties") as? String)
        ?.takeIf { it.isNotBlank() }
        ?.let { candidates += file(it) }

    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        val local = Properties().apply {
            FileInputStream(localPropsFile).use { load(it) }
        }
        local.getProperty("he.keystore.properties")?.takeIf { it.isNotBlank() }?.let {
            candidates += file(it)
        }
    }

    candidates += rootProject.file("keystore.properties")

    val propsFile = candidates.firstOrNull { it.isFile } ?: return null
    return Properties().apply {
        FileInputStream(propsFile).use { load(it) }
    }
}

val keystoreProps: Properties? = loadKeystoreProperties()

android {
    namespace = "com.harnessengineering.hesolardesignsuite"
    compileSdk = 34

    defaultConfig {
        applicationId = keystoreProps?.getProperty("applicationId")
            ?.takeIf { it.isNotBlank() }
            ?: "com.harnessengineering.hesolardesignsuite"
        minSdk = 24
        targetSdk = 34
        // Play last published versionCode was 1645; this release is 1646.
        versionCode = keystoreProps?.getProperty("versionCode")?.toIntOrNull() ?: 1646
        versionName = keystoreProps?.getProperty("versionName")?.takeIf { it.isNotBlank() } ?: "1.10.0"
    }

    signingConfigs {
        create("release") {
            val props = keystoreProps
            if (props != null) {
                val storePath = props.getProperty("storeFile")
                    ?: error("keystore.properties missing storeFile")
                storeFile = file(storePath)
                storePassword = props.getProperty("storePassword")
                    ?: error("keystore.properties missing storePassword")
                keyAlias = props.getProperty("keyAlias")
                    ?: error("keystore.properties missing keyAlias")
                keyPassword = props.getProperty("keyPassword")
                    ?: error("keystore.properties missing keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
