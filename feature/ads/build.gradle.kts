plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hereliesaz.logkitty.feature.ads"
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
    implementation(project(":app"))

    // The ad SDK + AD_ID permission live in this module so they're only pulled in with it.
    implementation(libs.play.services.ads)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    // fillMaxWidth/height live in compose-foundation (not pulled by compose-ui alone).
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
