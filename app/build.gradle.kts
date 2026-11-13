plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    jacoco
    alias(libs.plugins.dependency.check)
}

android {
    namespace = "com.sclera.sclerago"
    // API 37 (Android 17): matches targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sclera.sclerago"
        // 26: the Bluetooth / Wi-Fi / network scanners (ported from AssetOnBoard) need Android 8.0+.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // The QA environment, and the coverage data the unit-test report is built from.
            buildConfigField("String", "ACCOUNTS_URL", "\"https://qa-accounts.sclera.com\"")
            buildConfigField("String", "APP_URL", "\"https://qa-app.sclera.com\"")
            enableUnitTestCoverage = true
        }
        release {
            // Production.
            buildConfigField("String", "ACCOUNTS_URL", "\"https://accounts.sclera.com\"")
            buildConfigField("String", "APP_URL", "\"https://app.sclera.com\"")
            // R8: shrink, optimize and obfuscate the whole app (AGP 9.0 has no stable `optimization {}` DSL).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Builds the locale list for the per-app language setting (Android 13+) from the values-* folders.
        generateLocaleConfig = true
    }
    testOptions {
        // Robolectric needs the merged resources to resolve strings in unit tests.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.video)
    implementation(libs.camera.view)
    implementation(libs.exifinterface)
    // Bluetooth / Wi-Fi / access point scanning (scan/ package)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    // Networking
    implementation(libs.androidx.webkit)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ext.junit)
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    //Requery
    implementation("com.github.requery:sqlite-android:3.49.0")

    //Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

// ---------------------------------------------------------------- Code coverage (JaCoCo)
// `./gradlew jacocoTestReport` runs the unit + Robolectric UI tests and writes an HTML/XML report to
// app/build/reports/jacoco/. `./gradlew jacocoCoverageVerification` fails below 80% line coverage.
// Only code with nothing to execute is excluded: generated (Dagger/Hilt/Compose/R/BuildConfig) classes,
// the icon path tables and the bundled data tables (OUI vendor list, Apple model list, seed data).
jacoco { toolVersion = "0.8.16" }

// Robolectric reloads classes in its own sandbox; JaCoCo must still see them.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val coverageExcludes = listOf(
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*_Factory*.*", "**/*_MembersInjector*.*", "**/Hilt_*.*", "**/*_HiltModules*.*", "**/*_GeneratedInjector*.*",
    "**/dagger/hilt/**", "**/hilt_aggregated_deps/**", "**/*ComposableSingletons*.*",
    "**/presentation/icons/**", "**/data/local/SeedData*.*", "**/data/local/SeedPhotos*.*",
    "**/services/scan/data/**", "**/di/*_*.*",
)

val coverageClasses = fileTree(layout.buildDirectory.dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs")) { exclude(coverageExcludes) }
val coverageExec = fileTree(layout.buildDirectory) { include("outputs/unit_test_code_coverage/debugUnitTest/*.exec", "jacoco/*.exec") }

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Runs the unit tests and writes the line-coverage report."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(layout.projectDirectory.dir("src/main/java"))
    executionData.setFrom(coverageExec)
    reports {
        html.required = true
        xml.required = true
    }
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Fails when line coverage is below 80%."
    dependsOn("jacocoTestReport")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(layout.projectDirectory.dir("src/main/java"))
    executionData.setFrom(coverageExec)
    violationRules {
        rule { limit { counter = "LINE"; minimum = "0.80".toBigDecimal() } }
    }
}

// ---------------------------------------------------------------- Dependency vulnerabilities (OWASP)
// `./gradlew dependencyCheckAnalyze` scans every dependency for known CVEs. Set NVD_API_KEY (free, from
// nvd.nist.gov) for a fast first run; without it the NVD download is slow and rate-limited.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    scanConfigurations = listOf("releaseRuntimeClasspath")
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
}
