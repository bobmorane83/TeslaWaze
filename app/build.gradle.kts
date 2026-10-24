import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Valeurs NON secrètes injectées depuis secrets.properties (gitignoré) ou l'environnement.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String, def: String = ""): String =
    (secrets.getProperty(key) ?: System.getenv(key) ?: def)

android {
    namespace = "com.wazaa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wazaa.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"

        buildConfigField("String", "TESLA_CLIENT_ID", "\"${secret("TESLA_CLIENT_ID")}\"")
        buildConfigField("String", "TESLA_REDIRECT_URI", "\"${secret("TESLA_REDIRECT_URI", "com.wazaa.app://oauth")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
}
