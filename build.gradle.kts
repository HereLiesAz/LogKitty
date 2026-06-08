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
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.android) apply false
}