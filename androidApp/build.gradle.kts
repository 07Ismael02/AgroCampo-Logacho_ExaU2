import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
}
