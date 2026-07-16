plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hereliesaz.logkitty.feature.github"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // The base app provides core + Compose + coroutines + OkHttp at runtime; AGP de-dupes anything
    // already in the base out of this split. Declared here only so the module compiles on its own.
    // Do NOT re-declare base libraries (OkHttp, coroutines) — they arrive transitively via :app and
    // the root subprojects{} version pins, and re-declaring risks R8 "defined multiple times".
    implementation(project(":app"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    // fillMaxWidth/height & lazy lists live in compose-foundation (not pulled by compose-ui alone).
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.kotlinx.coroutines.core)
    // GitHub REST client. Same version the base uses (aligned by the root subprojects{} block), so
    // AGP de-dupes it against the base and it isn't duplicated in this split. (org.json is in the
    // Android framework, so it needs no declaration.)
    implementation(libs.okhttp)
}
