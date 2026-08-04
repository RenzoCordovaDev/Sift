package steps

import io.appium.java_client.android.AndroidDriver
import io.cucumber.java.PendingException
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.openqa.selenium.By
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import support.AdbHelper
import support.ScenarioContext
import java.time.Duration
import java.util.logging.Logger

/**
 * Cucumber step definitions for the F1 call-screening E2E scenarios.
 *
 * **Execution environment**: tests run on the HOST JVM. The Appium driver connects to a
 * running Appium 2.x server that in turn controls an Android emulator via ADB.
 * [AdbHelper] executes `adb` subcommands directly from the host process.
 *
 * **Call blocking detection**: after [AdbHelper.simulateIncomingCall]:
 * - A BLOCKED call: the device stays silent — no incoming-call overlay appears. After
 *   [CALL_SETTLE_WAIT_MS] the Appium driver finds no element matching [ANSWER_LOCATOR].
 * - An ALLOWED call: the Android telecom stack surfaces an incoming-call screen containing
 *   an "Answer" button. The driver finds [ANSWER_LOCATOR] within [CALL_APPEAR_TIMEOUT].
 *
 * The "Answer" button is looked up on the AOSP dialer app. Its text is locale-dependent;
 * the test assumes the emulator system language is English (the project's working language).
 *
 * **Room DB assertions**: [AdbHelper.queryAttemptCount] reads the SQLite file directly via
 * `adb shell run-as <package> sqlite3 <db>`. This requires a `debuggable` APK (debug build).
 *
 * @param context Scenario-scoped state injected by PicoContainer (same instance as Hooks).
 * @param adb ADB command helper injected by PicoContainer.
 */
class CallScreeningSteps(
    private val context: ScenarioContext,
    private val adb: AdbHelper
) {

    private val log: Logger = Logger.getLogger(CallScreeningSteps::class.java.name)

    // ─── Given steps ─────────────────────────────────────────────────────────

    /**
     * Inserts [phoneNumber] as a contact in the device address book.
     *
     * Uses [AdbHelper.insertContact] which calls `adb shell content insert` against
     * `ContactsContract`. The raw_contact ID is stored in [ScenarioContext.insertedContactIds]
     * so [support.Hooks.tearDown] can clean it up after the scenario.
     *
     * @param phoneNumber E.164 number to insert (e.g. "+12025550173").
     */
    @Given("the contact {string} exists in the device address book")
    fun contactExistsInAddressBook(phoneNumber: String) {
        val rawId = adb.insertContact(phoneNumber)
        assertTrue(rawId.isNotBlank()) { "Failed to insert contact $phoneNumber into the device address book" }
        context.insertedContactIds.add(rawId)
        log.info("Given: contact $phoneNumber inserted (raw_id=$rawId)")
    }

    /**
     * Asserts that [phoneNumber] is NOT stored as a contact on the device.
     *
     * After [support.Hooks.setUp] clears app data with `pm clear`, no test contacts exist.
     * This step documents the pre-condition explicitly so the scenario is self-explanatory.
     *
     * The absence is implicitly guaranteed by the [support.Hooks] setup sequence; this step
     * verifies it by querying the contacts provider via `content query`.
     *
     * @param phoneNumber E.164 number expected to be absent (e.g. "+12025550182").
     */
    @Given("{string} is not in the device address book")
    fun numberIsNotInAddressBook(phoneNumber: String) {
        val result = adb.queryContactCount(phoneNumber)
        assertEquals(0, result) {
            "$phoneNumber was found in the device address book but should not be. " +
                "The @Before hook calls pm clear which removes app data but NOT device contacts — " +
                "ensure no prior test scenario left this contact behind."
        }
        log.info("Given: $phoneNumber confirmed absent from address book (count=$result)")
    }

    /**
     * Pre-populates the Room `call_attempts` table with [attemptCount] blocked attempts for
     * [phoneNumber] so that the scenario can start with a known history state.
     *
     * The app must already be running (Hooks.setUp launches it) so the DB file exists.
     * Writes directly via `adb shell run-as <pkg> sqlite3` rather than through the app,
     * which guarantees the write is visible to Room on the next DB read.
     *
     * @param phoneNumber E.164 number whose history is to be pre-populated.
     * @param attemptCount Number of blocked attempts to record.
     */
    @Given("the database already has {int} blocked attempt(s) recorded for {string}")
    fun databaseHasBlockedAttempts(attemptCount: Int, phoneNumber: String) {
        adb.insertCallAttempt(phoneNumber, attemptCount)
        val stored = adb.queryAttemptCount(phoneNumber)
        assertEquals(attemptCount, stored) {
            "Pre-populated attempt count for $phoneNumber should be $attemptCount but DB shows $stored"
        }
        log.info("Given: pre-populated $attemptCount attempt(s) for $phoneNumber")
    }

    /**
     * Marks the scenario as pending because there is no Settings UI in F1 to disable the
     * call-screening filter. The DataStore file uses protobuf binary format that cannot be
     * safely written from an ADB shell command without a protobuf tool. This scenario will be
     * implemented in F4 when the Settings screen allows toggling the filter through the UI.
     *
     * **Alternative path to unlock this scenario earlier** (if desired before F4):
     * - Add a debug-only BroadcastReceiver that accepts an Intent extra
     *   `com.callbloqued.sift.SET_FILTER_ENABLED` → calls `SettingsRepository.setFilterEnabled(false)`.
     * - Grant `BROADCAST_STICKY` or use `LocalBroadcastManager` with a debug flag.
     * This would be a test-only code path added under `src/debug/` so it never ships in release.
     *
     * @throws PendingException always — marks the scenario as pending in the Cucumber report.
     */
    @Given("the call-screening filter is disabled")
    fun callScreeningFilterIsDisabled(): Nothing {
        throw PendingException(
            "Cannot disable filter without Settings UI (F4) or a debug BroadcastReceiver. " +
                "Scenario is pending until F4 Settings screen is implemented."
        )
    }

    // ─── When steps ──────────────────────────────────────────────────────────

    /**
     * Simulates an incoming call from [phoneNumber] via `adb emu gsm call`.
     *
     * The emulator console processes the GSM call command and routes it to the Android
     * telecom stack, which triggers [IncomingCallScreeningService.onScreenCall] within
     * approximately 500 ms. A [CALL_SETTLE_WAIT_MS] sleep gives the service coroutine time
     * to finish before the subsequent Then steps inspect the outcome.
     *
     * @param phoneNumber Number to use as the simulated caller (E.164 format).
     */
    @When("an incoming call arrives from {string}")
    fun incomingCallArrives(phoneNumber: String) {
        context.lastSimulatedCallNumber = phoneNumber
        adb.simulateIncomingCall(phoneNumber)
        Thread.sleep(CALL_SETTLE_WAIT_MS)
        log.info("When: simulated call from $phoneNumber — waiting ${CALL_SETTLE_WAIT_MS}ms for service response")
    }

    // ─── Then steps ──────────────────────────────────────────────────────────

    /**
     * Asserts that the device is currently displaying an incoming-call screen.
     *
     * The check looks for an element whose text is "Answer" using [ANSWER_LOCATOR]. On AOSP
     * emulators (Android 10–15) the incoming-call UI is rendered by the system dialer and
     * contains a labelled "Answer" button accessible via UIAutomator2 across all activities.
     *
     * After confirming the call is ringing the simulated call is cancelled via ADB so the
     * emulator returns to idle state before the next step or scenario.
     */
    @Then("the call rings on the device")
    fun callRingsOnDevice() {
        val driver = requireDriver()
        val ringing = waitForCallScreen(driver, appearing = true)
        assertTrue(ringing) {
            "Expected an incoming-call screen ('Answer' button) to appear within " +
                "${CALL_APPEAR_TIMEOUT.toSeconds()}s of the simulated call, but none was found. " +
                "The call may have been blocked by IncomingCallScreeningService."
        }
        log.info("Then: incoming-call screen confirmed (call is ringing)")

        // Clean up: cancel the ringing call so the emulator is back to idle for subsequent steps.
        context.lastSimulatedCallNumber?.let { adb.cancelSimulatedCall(it) }
        context.lastSimulatedCallNumber = null
    }

    /**
     * Asserts that NO incoming-call screen appeared after the simulated call, indicating the
     * call was silently blocked by [IncomingCallScreeningService].
     *
     * The check waits [CALL_APPEAR_TIMEOUT] and verifies that [ANSWER_LOCATOR] remains absent
     * throughout. A call that is allowed would appear within ~2 s on a normally loaded emulator,
     * so the 5 s timeout provides a comfortable margin.
     */
    @Then("the call does not ring on the device")
    fun callDoesNotRingOnDevice() {
        val driver = requireDriver()
        val ringing = waitForCallScreen(driver, appearing = false)
        assertTrue(!ringing) {
            "Expected the call to be blocked silently (no incoming-call screen), but an " +
                "'Answer' element appeared within ${CALL_APPEAR_TIMEOUT.toSeconds()}s. " +
                "Check that IncomingCallScreeningService received ROLE_CALL_SCREENING."
        }
        log.info("Then: no incoming-call screen — call was blocked silently")
    }

    /**
     * Asserts that the Room database contains NO `call_attempts` row for [phoneNumber].
     *
     * A zero count confirms that [EvaluateIncomingCallUseCase] returned Allow without
     * recording an attempt, which is the expected outcome for known contacts and for callers
     * that exceeded the threshold.
     *
     * @param phoneNumber E.164 number whose absence from the `call_attempts` table is asserted.
     */
    @Then("no call-attempt record exists in the database for {string}")
    fun noCallAttemptRecordExists(phoneNumber: String) {
        val count = adb.queryAttemptCount(phoneNumber)
        assertEquals(0, count) {
            "Expected no call-attempt record for $phoneNumber but the database shows $count attempt(s)."
        }
        log.info("Then: confirmed no call-attempt record for $phoneNumber")
    }

    /**
     * Asserts that the Room database contains exactly [expectedCount] blocked attempts for
     * [phoneNumber].
     *
     * @param expectedCount The exact `attempt_count` value expected in the `call_attempts` table.
     * @param phoneNumber E.164 number whose attempt count is asserted.
     */
    @Then("the database shows {int} blocked attempt(s) for {string}")
    fun databaseShowsBlockedAttempts(expectedCount: Int, phoneNumber: String) {
        val actual = adb.queryAttemptCount(phoneNumber)
        assertEquals(expectedCount, actual) {
            "Expected $expectedCount blocked attempt(s) for $phoneNumber " +
                "but the database shows $actual."
        }
        log.info("Then: database confirmed $actual blocked attempt(s) for $phoneNumber")
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun requireDriver() = checkNotNull(context.driver) {
        "AndroidDriver is null — the @Before hook did not complete successfully."
    }

    /**
     * Waits up to [CALL_APPEAR_TIMEOUT] for the incoming-call screen to appear or be absent.
     *
     * @param driver Active Appium driver for the current scenario.
     * @param appearing `true` to wait for the "Answer" element to appear; `false` to confirm absence.
     * @return `true` if the incoming-call screen was found; `false` if it was not found within
     *   the timeout period.
     */
    private fun waitForCallScreen(driver: AndroidDriver, appearing: Boolean): Boolean {
        val wait = WebDriverWait(driver, CALL_APPEAR_TIMEOUT)
        return try {
            if (appearing) {
                wait.until(ExpectedConditions.presenceOfElementLocated(ANSWER_LOCATOR))
                true
            } else {
                wait.until(ExpectedConditions.invisibilityOfElementLocated(ANSWER_LOCATOR))
                false
            }
        } catch (e: TimeoutException) {
            !appearing
        }
    }

    companion object {
        /**
         * Milliseconds to wait after `adb emu gsm call` before checking the call outcome.
         *
         * The IncomingCallScreeningService runs on Dispatchers.IO; the coroutine,
         * DB lookup, and respondToCall() typically complete in < 500 ms on an emulator.
         * Three seconds provides a comfortable margin without slowing scenarios significantly.
         */
        private const val CALL_SETTLE_WAIT_MS = 3_000L

        /**
         * How long [WebDriverWait] polls for the incoming-call screen element.
         *
         * For a BLOCKED call: the element must remain absent throughout this duration.
         * For an ALLOWED call: the element must appear within this window.
         * Five seconds covers Android telecom + system dialer rendering latency on a
         * normally loaded AVD emulator.
         */
        private val CALL_APPEAR_TIMEOUT: Duration = Duration.ofSeconds(5)

        /**
         * XPath locator for the "Answer" button on the AOSP incoming-call screen.
         *
         * UIAutomator2 can find this element regardless of which app is in the foreground
         * because the incoming-call UI is a system overlay rendered by `com.android.dialer`.
         * The `@text` attribute uses a case-insensitive substring match to tolerate minor
         * locale differences (e.g. "ANSWER" vs "Answer").
         *
         * If the emulator uses a non-AOSP dialer that labels the button differently, update
         * this locator. Alternatives: `@content-desc`, `@resource-id` (com.android.dialer:id/
         * answer_and_release_button on older AOSP builds).
         */
        private val ANSWER_LOCATOR: By = By.xpath(
            "//*[translate(@text,'answer','ANSWER')='ANSWER' " +
                "or contains(translate(@content-desc,'answer','ANSWER'),'ANSWER')]"
        )
    }
}
