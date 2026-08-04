package support

import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.net.URL
import java.time.Duration
import java.util.logging.Logger

/**
 * Factory that creates a configured [AndroidDriver] connected to the local Appium server.
 *
 * The driver uses the UIAutomator2 backend, which is the recommended Appium 2.x driver for
 * Android. It controls the emulator over ADB and can inspect any visible UI element,
 * including system overlays such as the incoming-call screen.
 *
 * **Prerequisites** (see also `e2e/build.gradle.kts` header):
 * - Appium 2.x server running: `appium --port 4723`
 * - UIAutomator2 driver installed: `appium driver install uiautomator2`
 * - A running Android emulator with the Sift `debug` APK installed
 *
 * **Configuration via system properties** (set via Gradle `-D` flags):
 * - `appium.url` — Appium server URL, default `http://127.0.0.1:4723`
 * - `adb.serial` — ADB device serial, default empty (uses the only connected device)
 */
object AppiumDriverFactory {

    private val log: Logger = Logger.getLogger(AppiumDriverFactory::class.java.name)

    /** Default Appium 2.x server URL. Overridable via `-Dappium.url=...`. */
    private val appiumUrl: String
        get() = System.getProperty("appium.url", "http://127.0.0.1:4723")

    /** ADB serial of the target emulator. Empty string means "use the only connected device". */
    private val adbSerial: String
        get() = System.getProperty("adb.serial", "")

    /**
     * Creates and returns a new [AndroidDriver] session pointed at the Sift app.
     *
     * The session uses `noReset=true` because the [Hooks] class manages app state resets
     * explicitly via `pm clear` + permission grants before each scenario. Letting Appium
     * reset the app would interfere with that lifecycle and double-clear the data.
     *
     * [UiAutomator2Options.setAutoGrantPermissions] is `false` for the same reason — we
     * grant permissions manually after `pm clear` so they are applied at the right moment
     * and we can verify that our grant succeeded.
     *
     * The implicit wait is set to zero: step definitions use explicit [org.openqa.selenium.support.ui.WebDriverWait]
     * calls with deliberate timeouts so that blocking vs. ringing detection is time-bounded
     * and does not rely on Appium's own timeout heuristics.
     *
     * @return A ready [AndroidDriver] session. The caller is responsible for calling [AndroidDriver.quit]
     *   when the scenario ends.
     * @throws org.openqa.selenium.WebDriverException if the Appium server is unreachable or the
     *   emulator is not connected.
     */
    fun create(): AndroidDriver {
        val options = UiAutomator2Options()
            .setAppPackage(AdbHelper.APP_PACKAGE)
            .setAppActivity(".presentation.MainActivity")
            .setNoReset(true)
            .setAutoGrantPermissions(false)
            .setNewCommandTimeout(Duration.ofSeconds(60))

        if (adbSerial.isNotBlank()) {
            options.setUdid(adbSerial)
        }

        log.info("AppiumDriverFactory.create: connecting to $appiumUrl" +
            (if (adbSerial.isNotBlank()) " device=$adbSerial" else ""))

        val driver = AndroidDriver(URL(appiumUrl), options)
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0))

        log.info("AppiumDriverFactory.create: session ${driver.sessionId} ready")
        return driver
    }
}
