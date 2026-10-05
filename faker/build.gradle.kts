plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionNameProp: String = (project.findProperty("versionName") as? String)?.takeIf { it.isNotBlank() }
    ?: project.rootProject.file("VERSION").readText().trim()
val versionCodeProp: Int = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1

android {
    namespace = "com.aholic.fakegxzq.faker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aholic.fakegxzq.faker"
        minSdk = 24
        targetSdk = 34
        versionCode = versionCodeProp
        versionName = versionNameProp
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    compileOnly("de.robv.android.xposed:api:82")
}
