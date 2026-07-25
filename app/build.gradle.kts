import java.util.Properties
import java.io.FileInputStream
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

// versionCode source. CI passes `-PversionBuild=$(git rev-list --count HEAD)` so every Play upload
// gets a strictly-increasing code (commit count only ever grows). When the override is absent (local
// builds, Android Studio) we keep the previous behavior: auto-increment a counter in
// version.properties on each build task.
val versionBuildOverride = project.findProperty("versionBuild")?.toString()?.toIntOrNull()
var buildNumber = versionBuildOverride ?: (versionProps.getProperty("build")?.toIntOrNull() ?: 0)

// Automatic build-number increment: bump on every build that produces an artifact, regardless of
// environment (CLI, Android Studio, CI) or which build task is invoked. This runs at configuration
// time so the new number flows into versionCode/versionName below. Writing version.properties also
// invalidates the configuration cache, so the next build re-runs this block and increments again.
val isBuildTask = gradle.startParameter.taskNames.any { taskName ->
    val name = taskName.substringAfterLast(':').lowercase()
    name.startsWith("assemble") || name.startsWith("bundle") ||
        name.startsWith("install") || name.startsWith("package") || name == "build"
}

// Only auto-increment/persist locally; when an explicit -PversionBuild override is supplied (CI),
// use it verbatim and leave version.properties untouched.
if (versionBuildOverride == null && isBuildTask) {
    buildNumber++
    versionProps.setProperty("build", buildNumber.toString())
    versionPropsFile.writer().use { versionProps.store(it, null) }
}

android {
    namespace = "com.hereliesaz.logkitty"
    compileSdk = 37

    // On-demand feature modules. Delivered individually on Google Play; fused into the universal /
    // standalone APK (see each module's <dist:fusing>) for the sideloaded GitHub build.
    dynamicFeatures += setOf(":feature:stats", ":feature:github")

    // The in-app About/Help reader (InfoScreen) displays the project's own docs. They're copied from
    // the canonical README + docs/ into a generated assets directory at build time, so there's a
    // single source of truth and no committed duplicates. Wired via the Variant API below
    // (androidComponents.onVariants) so the task dependency reaches every consumer (merge + lint).

    defaultConfig {
        applicationId = "com.hereliesaz.logkitty"
        minSdk = 30
        targetSdk = 37
        // Give buildNumber its own 5-digit slot so a commit-count buildNumber (up to 99_999) can't
        // overflow into the patch/minor/major digits and collide. Stays well under Android's
        // 2_100_000_000 versionCode cap (envelope: major <= 2, minor/patch <= 99, build <= 99_999).
        versionCode = (major * 10000 + minor * 100 + patch) * 100000 + buildNumber
        versionName = "$major.$minor.$patch.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Inject API Key
        val apiKey = getLocalProperty("FONTS_API_KEY", rootProject.projectDir)
        buildConfigField("String", "FONTS_API_KEY", "\"$apiKey\"")
        manifestPlaceholders["FONTS_API_KEY"] = apiKey // THIS WAS THE MISSING LINE



        // GitHub OAuth app client id for the device-flow sign-in (optional; PAT works without it).
        // Public value (no secret in device flow); supply via local.properties/env to enable the
        // "Sign in with GitHub" button. Blank by default → the UI shows PAT entry only.
        val githubOauthClientId = getLocalProperty("GITHUB_OAUTH_CLIENT_ID", rootProject.projectDir)
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$githubOauthClientId\"")

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
            // Strip unused resources alongside R8 code shrinking. Resources referenced only across
            // module boundaries (the dynamic-feature dist:title strings) are protected by
            // res/raw/keep.xml. Verify on a release build that nothing needed was removed.
            isShrinkResources = true
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

// Copies the canonical README + user-facing docs into a generated assets directory for the in-app
// About/Help reader (InfoScreen). A typed task with a declared output directory lets the Variant API
// (below) carry the task dependency to every consumer automatically.
abstract class CopyInAppDocsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val docFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val root = outputDir.get().asFile
        root.deleteRecursively()
        val docs = File(root, "docs").apply { mkdirs() }
        docFiles.files.forEach { src ->
            if (src.exists()) src.copyTo(File(docs, src.name), overwrite = true)
        }
    }
}

val copyInAppDocs = tasks.register<CopyInAppDocsTask>("copyInAppDocs") {
    description = "Bundles README + user-facing docs into assets for the in-app About/Help reader."
    docFiles.from(
        rootProject.file("README.md"),
        rootProject.file("docs/PRIVACY_POLICY.md"),
        rootProject.file("docs/PERMISSIONS.md"),
        rootProject.file("docs/conduct.md"),
    )
    outputDir.set(layout.buildDirectory.dir("generated/inAppDocs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyInAppDocs, CopyInAppDocsTask::outputDir)
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
                    useVersion("4.1.121.Final")
                    because("Build-tooling transitive only (never shipped); pin Netty to the latest 4.1.x security release")
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
                // Some Play/GMS transitives still drag in an old Fragment (1.x), which makes the
                // InvalidFragmentVersionForActivityResult lint fatal for our
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

    // WorkManager: used for log cleanup and background tasks.
    implementation(libs.androidx.work.runtime.ktx)

    // Play Feature Delivery: install/observe on-demand modules at runtime via SplitInstallManager.
    implementation(libs.play.feature.delivery)
    implementation(libs.play.feature.delivery.ktx)

    // Play in-app updates (flexible) — prompts Play users to update without leaving the app.
    implementation(libs.play.app.update)

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

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Billing
    val billing_version = "9.1.0"
    implementation("com.android.billingclient:billing:$billing_version")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
