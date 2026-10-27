import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val agroLocal = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val agroApiBaseUrl = (agroLocal.getProperty("agro.api.baseUrl") ?: "").trim()
require(!agroApiBaseUrl.contains('"') && !agroApiBaseUrl.contains('\\')) {
    "agro.api.baseUrl contiene caracteres no admitidos"
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
}

android {
    namespace = "pe.upeu.agrosierra"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pe.upeu.agrosierra"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true; buildConfig = true }
    defaultConfig {
        buildConfigField("String", "AGRO_API_BASE_URL", "\"$agroApiBaseUrl\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.kotlinx.json)
}
