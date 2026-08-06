// Force secure versions of transitive dependencies that ride in on the build / plugin classpath
// (AGP's apksigner pulls BouncyCastle; other build tooling pulls Netty / jose4j / jdom2 /
// httpclient / commons-lang3). These are what Dependabot flags against settings.gradle.kts.
// `eachDependency` only rewrites dependencies that are actually present, so any force that doesn't
// apply is a harmless no-op. Mirrored in app/build.gradle.kts for the app's own configurations.
buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            val g = requested.group
            val n = requested.name
            when {
                g == "com.google.guava" && n == "guava" -> {
                    val suffix = if (requested.version?.endsWith("-android") == true) "-android" else "-jre"
                    useVersion("33.3.1$suffix")
                    because("Security fixes: CVE-2023-2976 & CVE-2020-8908 (insecure temp-dir use / info disclosure)")
                }
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
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Pin the same transitive-dependency versions in every module (base, core, dynamic features). The
// base already does this for itself; without the same pins in a dynamic feature module, a library
// such as Guava resolves to a different version there, so AGP can't recognize it as already present
// in the base and R8 fails with "defined multiple times". (Also keeps the security forces uniform.)
subprojects {
    configurations.all {
        // Note: Do NOT exclude listenablefuture globally or guava:listenablefuture, because libraries
        // like androidx.concurrent:concurrent-futures or androidx.profileinstaller require
        // com.google.common.util.concurrent.ListenableFuture at runtime.
        resolutionStrategy.eachDependency {
            val g = requested.group
            val n = requested.name
            when {
                g == "com.google.guava" && n == "guava" -> {
                    val suffix = if (requested.version?.endsWith("-jre") == true) "-jre" else "-android"
                    useVersion("33.3.1$suffix")
                    because("Align Guava across modules so dynamic-feature dedup works (CVE-2023-2976, CVE-2020-8908)")
                }
                g == "com.google.protobuf" && n == "protobuf-kotlin" -> {
                    useVersion("3.25.5"); because("Security fix")
                }
                g == "io.netty" && !n.startsWith("netty-tcnative") -> {
                    useVersion("4.1.121.Final"); because("Latest 4.1.x security release")
                }
                g == "org.bouncycastle" && n.endsWith("-jdk18on") -> {
                    useVersion("1.84"); because("Security fixes")
                }
                g == "org.apache.commons" && n == "commons-lang3" -> {
                    useVersion("3.18.0"); because("Security fix: CVE-2025-48924")
                }
                g == "org.apache.httpcomponents" && n == "httpclient" -> {
                    useVersion("4.5.14"); because("Security fix: CVE-2020-13956")
                }
                g == "org.jdom" && n == "jdom2" -> {
                    useVersion("2.0.6.1"); because("Security fix: XXE")
                }
                g == "org.bitbucket.b_c" && n == "jose4j" -> {
                    useVersion("0.9.6"); because("Security fix")
                }
                g == "androidx.fragment" && n == "fragment" -> {
                    useVersion("1.6.2"); because("registerForActivityResult lint requires androidx.fragment >= 1.3.0")
                }
            }
        }
    }
}