import java.util.Properties
import java.io.FileInputStream
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Helper to load properties securely
fun getLocalProperty(key: String, rootDir: File): String {
    val properties = Properties()
    val localProperties = File(rootDir, "local.properties")
    if (localProperties.exists()) {
        properties.load(FileInputStream(localProperties))
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
var buildNumber = versionProps.getProperty("build")?.toIntOrNull() ?: 0

// Automatic build-number increment: bump on every build that produces an artifact, regardless of
// environment (CLI, Android Studio, CI) or which build task is invoked. This runs at configuration
// time so the new number flows into versionCode/versionName below. Writing version.properties also
// invalidates the configuration cache, so the next build re-runs this block and increments again.
val isBuildTask = gradle.startParameter.taskNames.any { taskName ->
    val name = taskName.substringAfterLast(':').lowercase()
    name.startsWith("assemble") || name.startsWith("bundle") ||
        name.startsWith("install") || name.startsWith("package") || name == "build"
}

if (isBuildTask) {
    buildNumber++
    versionProps.setProperty("build", buildNumber.toString())
    versionPropsFile.writer().use { versionProps.store(it, null) }
}

android {
    namespace = "com.hereliesaz.logkitty"
    compileSdk = 37

    // On-demand feature modules. Delivered individually on Google Play; fused into the universal /
    // standalone APK (see each module's <dist:fusing>) for the sideloaded GitHub build.
    dynamicFeatures += setOf(":feature:stats", ":feature:ads")

    defaultConfig {
        applicationId = "com.hereliesaz.logkitty"
        minSdk = 30
        targetSdk = 37
        versionCode = major * 1000000 + minor * 10000 + patch * 100 + buildNumber
        versionName = "$major.$minor.$patch.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Inject API Key
        val apiKey = getLocalProperty("FONTS_API_KEY", rootProject.projectDir)
        buildConfigField("String", "FONTS_API_KEY", "\"$apiKey\"")
        manifestPlaceholders["FONTS_API_KEY"] = apiKey // THIS WAS THE MISSING LINE

        // AdMob banner unit id. Debug uses Google's official TEST unit so development clicks don't
        // generate invalid traffic on the live unit; release swaps in the real unit (below). The
        // app ID and AD_ID permission now live in the :feature:ads module. The base only passes this
        // unit id to that module's banner. (Unit IDs are public — they ship in the APK.)
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")

        // Build Tools Config
        val toolsOwner = project.findProperty("build.tools.owner") as? String ?: "HereLiesAz"
        val toolsRepo = project.findProperty("build.tools.repo") as? String ?: "LogKitty-buildtools"
        buildConfigField("String", "BUILD_TOOLS_OWNER", "\"$toolsOwner\"")
        buildConfigField("String", "BUILD_TOOLS_REPO", "\"$toolsRepo\"")
        buildConfigField("String", "GH_TOKEN", "\"${System.getenv("GH_TOKEN") ?: ""}\"")
        buildConfigField("String", "REPO_OWNER", "\"HereLiesAz\"")
        buildConfigField("String", "REPO_NAME", "\"LogKitty\"")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        debug {
            // signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Production banner ad-unit (real). Debug keeps the test unit from defaultConfig so our
            // own development clicks don't generate invalid traffic on the live unit. An optional
            // ADMOB_BANNER_UNIT_ID in local.properties/env overrides it (e.g. for a staging unit).
            val overrideBannerUnitId = getLocalProperty("ADMOB_BANNER_UNIT_ID", rootProject.projectDir)
            val bannerUnitId = if (overrideBannerUnitId.isNotBlank()) overrideBannerUnitId
                else "ca-app-pub-7304740804770627/1839035745"
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$bannerUnitId\"")
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
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
            val g = requested.group
            val n = requested.name
            when {
                g == "commons-logging" && n == "commons-logging" -> {
                    useTarget("org.slf4j:jcl-over-slf4j:1.7.30")
                    because("Avoids duplicate classes with jcl-over-slf4j")
                }
                g == "com.google.guava" && n == "guava" -> {
                    // App module runs on Android, so default to the -android flavor unless the
                    // dependency explicitly asked for -jre.
                    val suffix = if (requested.version?.endsWith("-jre") == true) "-jre" else "-android"
                    useVersion("33.3.1$suffix")
                    because("Security fixes: CVE-2023-2976 & CVE-2020-8908 (insecure temp-dir use / info disclosure)")
                }
                g == "com.google.protobuf" && n == "protobuf-kotlin" -> {
                    useVersion("3.25.5")
                    because("Security fix")
                }
                // Netty artifacts are versioned together; force the whole family (except the
                // separately-versioned tcnative natives) to the latest 4.1 security release.
                g == "io.netty" && !n.startsWith("netty-tcnative") -> {
                    useVersion("4.1.133.Final")
                    because("Security fixes: CVE-2025-67735, CVE-2026-42583, CVE-2026-42587, et al.")
                }
                g == "org.bouncycastle" && n.endsWith("-jdk18on") -> {
                    useVersion("1.84")
                    because("Security fixes: CVE-2026-0636 (LDAP), covert timing channel, broken crypto")
                }
                g == "org.apache.commons" && n == "commons-lang3" -> {
                    useVersion("3.18.0")
                    because("Security fix: CVE-2025-48924 uncontrolled recursion")
                }
                g == "org.apache.httpcomponents" && n == "httpclient" -> {
                    useVersion("4.5.14")
                    because("Security fix: cross-site scripting (CVE-2020-13956)")
                }
                g == "org.jdom" && n == "jdom2" -> {
                    useVersion("2.0.6.1")
                    because("Security fix: XXE injection")
                }
                g == "org.bitbucket.b_c" && n == "jose4j" -> {
                    useVersion("0.9.6")
                    because("Security fix: DoS via compressed JWE content")
                }
                // play-services-basement (via play-services-ads) drags in an old Fragment (1.x),
                // which makes the InvalidFragmentVersionForActivityResult lint fatal for our
                // registerForActivityResult calls. Force a version >= 1.3.0.
                g == "androidx.fragment" && n == "fragment" -> {
                    useVersion("1.6.2")
                    because("registerForActivityResult lint requires androidx.fragment >= 1.3.0")
                }
            }
        }
    }
}

dependencies {
    // Shared interfaces/constants for dynamic feature modules. `api` so feature modules, which
    // depend on :app, can compile against :core types (provided by the base at runtime).
    api(project(":core"))

    // Play Feature Delivery: install/observe on-demand modules at runtime via SplitInstallManager.
    implementation(libs.play.feature.delivery)
    implementation(libs.play.feature.delivery.ktx)

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

    implementation("androidx.compose.ui:ui-text-google-fonts:1.11.1")

    // Custom UI components we kept
    implementation(libs.dokar3.sheets.m3)
    implementation(libs.aznavrail)

    // Google Mobile Ads now lives in the on-demand :feature:ads module, not the base.

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
