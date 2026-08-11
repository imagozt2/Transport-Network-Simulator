import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localServicesFile = sequenceOf(
    rootProject.file("../config/local-services.properties"),
    rootProject.file("../config/local-services.properties.example"),
).firstOrNull { it.isFile }
    ?: error("No se ha encontrado la configuración local de servicios en /config")

val localServices = Properties().apply {
    localServicesFile.inputStream().use(::load)
}

fun localService(name: String): String =
    localServices.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: error("Falta la propiedad $name en ${localServicesFile.path}")

fun optionalLocalService(name: String, fallback: String): String =
    localServices.getProperty(name)?.trim()?.takeIf(String::isNotEmpty) ?: fallback

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val androidEnvironment = optionalLocalService("RMM_ANDROID_ENVIRONMENT", "local").lowercase()
require(androidEnvironment in setOf("local", "staging", "production")) {
    "RMM_ANDROID_ENVIRONMENT debe ser local, staging o production"
}

val androidApiBaseUrl = localService("RMM_API_ANDROID_BASE_URL").trimEnd('/') + "/"
val androidApiUri = URI(androidApiBaseUrl)
require(androidApiUri.scheme in setOf("http", "https") && !androidApiUri.host.isNullOrBlank()) {
    "RMM_API_ANDROID_BASE_URL debe ser una URL HTTP o HTTPS absoluta"
}
require(androidApiUri.path.endsWith("/api/rmm-app/v1/")) {
    "RMM_API_ANDROID_BASE_URL debe terminar en /api/rmm-app/v1"
}

android {
    namespace = "com.rmm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rmm.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "RMM_API_BASE_URL",
            buildConfigString(androidApiBaseUrl),
        )
        buildConfigField("String", "RMM_ENVIRONMENT", buildConfigString(androidEnvironment))
        manifestPlaceholders["rmmUsesCleartextTraffic"] = "false"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["rmmUsesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
