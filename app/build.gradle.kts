import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.junit5)
    jacoco
}

android {
    namespace = "com.callbloqued.sift"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callbloqued.sift"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Export Room schema files so migrations can be validated in version control.
        // Schema files live in app/schemas/ and are committed to the repo.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ─── Dependencies ────────────────────────────────────────────────────────────

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (BOM enforces consistent versions across all compose-* artifacts)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // Hilt — DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room — local database (entities and DAOs come in F1/F2)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore — preferences (preference keys come in F1)
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // Phone number normalisation (no usage yet — declared for F1)
    implementation(libs.libphonenumber)

    // Debug tooling
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ─── Test dependencies ──────────────────────────────────────────────────
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// ─── JUnit 5 for local unit tests ────────────────────────────────────────────

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// ─── JaCoCo — coverage gate (≥80 % on domain + data layers) ─────────────────
//
// The coverage gate is wired here so that `./gradlew check` fails when
// coverage drops below the project-mandated threshold.  Actual tests and
// source-set exclusions will be refined by the test-unitario agent in F1+.

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val jacocoTestReport by tasks.registering(JacocoReport::class) {
    dependsOn(tasks.named("testDebugUnitTest"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val buildDir = layout.buildDirectory.get().asFile
    classDirectories.setFrom(
        fileTree("$buildDir/tmp/kotlin-classes/debug") {
            // Exclude generated DI/Room code, data-binding, and the Application stub
            exclude(
                "**/di/**",
                "**/hilt_aggregated_deps/**",
                "**/*_HiltModules*",
                "**/*_Factory*",
                "**/*_MembersInjector*",
                "**/BuildConfig.*",
                "**/Manifest*.*",
                "**/R.class",
                "**/R\$*.class",
                // Presentation layer is excluded from the 80% gate (domain + data only)
                "**/presentation/**"
            )
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(buildDir) { include("**/*.exec", "**/*.ec") }
    )
}

val jacocoCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    dependsOn(jacocoTestReport)

    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    val buildDir = layout.buildDirectory.get().asFile
    classDirectories.setFrom(
        fileTree("$buildDir/tmp/kotlin-classes/debug") {
            exclude(
                "**/di/**",
                "**/hilt_aggregated_deps/**",
                "**/*_HiltModules*",
                "**/*_Factory*",
                "**/*_MembersInjector*",
                "**/BuildConfig.*",
                "**/Manifest*.*",
                "**/R.class",
                "**/R\$*.class",
                "**/presentation/**"
            )
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(buildDir) { include("**/*.exec", "**/*.ec") }
    )
}

tasks.named("check") {
    dependsOn(jacocoCoverageVerification)
}
