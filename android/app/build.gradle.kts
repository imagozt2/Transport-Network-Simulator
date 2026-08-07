import java.util.Properties

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
            "\"${localService("RMM_API_ANDROID_BASE_URL")}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
