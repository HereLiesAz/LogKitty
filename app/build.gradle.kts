plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import java.io.FileInputStream

// Helper to load properties securely
fun getLocalProperty(key: String): String {
    val properties = java.util.Properties()
    val localProperties = File(rootProject.projectDir, "local.properties")
    if (localProperties.exists()) {
        properties.load(localProperties.inputStream())
    }
    return properties.getProperty(key) ?: System.getenv(key) ?: ""
}

val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val major = versionProps.getProperty("major")?.toIntOrNull() ?: 1
val minor = versionProps.getProperty("minor")?.toIntOrNull() ?: 0
val patch = versionProps.getProperty("patch")?.toIntOrNull() ?: 0
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.hereliesaz.logkitty"
    compileSdk = 34 // Reverted to 34 as 36 is likely unstable/preview and causes issues

    defaultConfig {
        applicationId = "com.hereliesaz.logkitty"
        minSdk = 30
        targetSdk = 34
        versionCode = major * 1000000 + minor * 10000 + patch * 100 + buildNumber
        versionName = "$major.$minor.$patch.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    // Inject API Key (Moved out of signingConfigs to ensure it applies to all builds)
    defaultConfig {
        buildConfigField("String", "FONTS_API_KEY", "\"${getLocalProperty("FONTS_API_KEY")}\"")
        
        // Build Tools Config
        val toolsOwner = project.findProperty("build.tools.owner") as? String ?: "HereLiesAz"
        val toolsRepo = project.findProperty("build.tools.repo") as? String ?: "LogKitty-buildtools"
        buildConfigField("String", "BUILD_TOOLS_OWNER", "\"$toolsOwner\"")
        buildConfigField("String", "BUILD_TOOLS_REPO", "\"$toolsRepo\"")
        buildConfigField("String", "GH_TOKEN", "\"${System.getenv("GH_TOKEN") ?: ""}\"")
        buildConfigField("String", "REPO_OWNER", "\"HereLiesAz\"")
        buildConfigField("String", "REPO_NAME", "\"LogKitty\"")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        debug {
            // signingConfig = signingConfigs.getByName("debug") // Default debug signing is usually fine
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        jvmToolchain(17) // Standard for AGP 8+ compatibility
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("mime.types")
            excludes.add("META-INF/THIRD-PARTY.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/plexus/components.xml")
            excludes.add("plugin.properties")
            pickFirsts.add("META-INF/sisu/javax.inject.Named")
            pickFirsts.add("**/*.jnilib")
            pickFirsts.add("**/*.kotlin_builtins")
            pickFirsts.add("**/*.kotlin_module")
            pickFirsts.add("misc/registry.properties")
            pickFirsts.add("**/libjnidispatch.so")
        }
    }
}

configurations.all {
    exclude(group = "com.intellij", module = "annotations")
    resolutionStrategy {
        eachDependency {
            if (requested.group == "commons-logging" && requested.name == "commons-logging") {
                useTarget("org.slf4j:jcl-over-slf4j:1.7.30")
                because("Avoids duplicate classes with jcl-over-slf4j")
            }
            if (requested.group == "com.google.protobuf" && requested.name == "protobuf-kotlin") {
                useVersion("3.25.5")
                because("Security fix")
            }
            if (requested.group == "org.jdom" && requested.name == "jdom2") {
                useVersion("2.0.6.1")
                because("Security fix")
            }
            if (requested.group == "io.netty" && requested.name == "netty-codec-http2") {
                useVersion("4.1.124.Final")
                because("Security fix")
            }
            if (requested.group == "io.netty" && requested.name == "netty-handler") {
                useVersion("4.1.118.Final")
                because("Security fix")
            }
            if (requested.group == "org.bitbucket.b_c" && requested.name == "jose4j") {
                useVersion("0.9.6")
                because("Security fix")
            }
            if (requested.group == "io.netty" && requested.name == "netty-codec-http") {
                useVersion("4.1.129.Final")
                because("Security fix")
            }
        }
    }
}

dependencies {
    // Keep libraries needed for UI and logging
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")

    // Custom UI components we kept
    implementation(libs.dokar3.sheets.m3)
    implementation(libs.aznavrail)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
