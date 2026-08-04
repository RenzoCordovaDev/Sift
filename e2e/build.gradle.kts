// E2E test module — runs Gherkin scenarios via Cucumber-JVM + Appium (UIAutomator2).
//
// This is a PURE JVM module (not Android). Test code runs on the HOST machine and controls
// an Android emulator through an Appium server. It is intentionally excluded from the normal
// CI pipeline because CI has no emulator available; see LOCAL_AUTOMATION_SETUP.md for how to
// run E2E tests locally.
//
// Pre-requisites before running:
//   1. Android emulator is booted (e.g. via Android Studio AVD Manager or `emulator @<avd>`).
//   2. Appium server 2.x is running: `appium --port 4723`
//   3. UIAutomator2 driver is installed: `appium driver install uiautomator2`
//   4. The Sift debug APK is installed on the emulator:
//        ./gradlew :app:installDebug
//   5. ADB is in PATH (provided by Android SDK; see LOCAL_AUTOMATION_SETUP.md).
//
// Run command:
//   ./gradlew :e2e:test
//       -Dappium.url=http://127.0.0.1:4723    (default; override if server is remote)
//       -Dadb.serial=emulator-5554             (optional; required with multiple devices)

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    // Cucumber — Gherkin runner and step-definition engine
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.cucumber.picocontainer)

    // JUnit Platform suite runner — wires @Suite → @IncludeEngines("cucumber")
    testImplementation(libs.junit.platform.suite)
    testRuntimeOnly(libs.junit.platform.suite.engine)

    // JUnit 5 — assertion API used inside step definitions
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)

    // Appium Java client — connects to Appium server and drives UIAutomator2
    testImplementation(libs.appium.java.client)
}

tasks.test {
    useJUnitPlatform()
    // Propagate system properties so test code can read -D flags passed on the command line.
    systemProperty("appium.url", System.getProperty("appium.url", "http://127.0.0.1:4723"))
    systemProperty("adb.serial", System.getProperty("adb.serial", ""))
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
