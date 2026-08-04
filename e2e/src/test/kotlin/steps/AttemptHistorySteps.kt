package steps

import io.cucumber.java.PendingException
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import support.AdbHelper
import support.ScenarioContext
import java.util.logging.Logger

/**
 * Cucumber step definitions for the F2 attempt-history E2E scenarios.
 *
 * F2 adds the `blocked_call_log` Room table, which records each individual silent-block event
 * produced by [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase] via
 * [com.callbloqued.sift.domain.repository.CallLogRepository]. These steps verify that the
 * event is persisted correctly after a simulated call, and mark UI-dependent verification
 * as pending until the F4 history screen is implemented.
 *
 * **Verification strategy**: the host-side JVM test module has no direct access to Room APIs.
 * Database assertions therefore use `adb shell run-as <pkg> sqlite3` queries via [AdbHelper]
 * — the same technique used in F1 for the `call_attempts` table. This requires a `debuggable`
 * APK (debug build) and `sqlite3` available at `/system/bin/sqlite3` on the emulator image.
 *
 * **Pending scenario**: the `@HistoryScreen @PendingUI` scenario depends on the call history
 * Compose screen, which is planned for F4. The first UI-dependent [When] step throws
 * [PendingException] following the same pattern as the `@FilterDisabled @PendingUI` scenario
 * in `f1_call_screening_core.feature`.
 *
 * @param context Scenario-scoped state injected by PicoContainer (same instance as Hooks).
 * @param adb ADB command helper injected by PicoContainer.
 */
class AttemptHistorySteps(
    private val context: ScenarioContext,
    private val adb: AdbHelper
) {

    private val log: Logger = Logger.getLogger(AttemptHistorySteps::class.java.name)

    // ─── Given steps ─────────────────────────────────────────────────────────

    /**
     * Pre-populates the `blocked_call_log` table with one entry for [phoneNumber], giving
     * the history-screen scenario a known starting state without having to simulate a call.
     *
     * The inserted row uses [AdbHelper.insertBlockedCallLogEntry], which writes directly
     * via `adb shell run-as <pkg> sqlite3`. The insertion is verified by reading the count
     * back before the scenario continues.
     *
     * @param phoneNumber E.164 number to insert into the log (e.g. "+12025550182").
     */
    @Given("a blocked call from {string} was previously logged")
    fun aBlockedCallWasPreviouslyLogged(phoneNumber: String) {
        adb.insertBlockedCallLogEntry(phoneNumber)
        val count = adb.queryBlockedCallLogCount(phoneNumber)
        assertEquals(1, count) {
            "Expected 1 pre-populated blocked_call_log entry for $phoneNumber but found $count. " +
                "Ensure the app was launched by the @Before hook so the DB schema exists."
        }
        log.info("Given: pre-populated 1 blocked_call_log entry for $phoneNumber")
    }

    // ─── When steps ──────────────────────────────────────────────────────────

    /**
     * Marks the scenario as pending because the call history screen is not yet implemented.
     *
     * The screen will be delivered in F4 (UI phase). There is no Appium path to navigate
     * to a screen that does not exist in the app. Until the history Compose screen is
     * available this step throws [PendingException], causing the `@HistoryScreen @PendingUI`
     * scenario to appear as PENDING rather than FAILED in the Cucumber report.
     *
     * **To implement this step in F4**: replace the [PendingException] with Appium navigation
     * logic — e.g. tap the history bottom-nav item or navigate via the app's Intent — and
     * complete the corresponding [historyScreenDisplaysEntry] Then step.
     *
     * @throws PendingException always — marks the scenario as pending in the Cucumber report.
     */
    @When("the user opens the call history screen")
    fun userOpensCallHistoryScreen(): Nothing {
        throw PendingException(
            "The call history screen has not been implemented yet (planned for F4). " +
                "This scenario cannot be automated until the history Compose screen exists."
        )
    }

    // ─── Then steps ──────────────────────────────────────────────────────────

    /**
     * Asserts that the `blocked_call_log` table contains exactly [expectedCount] rows for
     * [phoneNumber].
     *
     * Each row in `blocked_call_log` represents one discrete silent-block event. This differs
     * from the `call_attempts` table, which stores one aggregate row per caller that is
     * updated (not appended) on each subsequent block.
     *
     * @param expectedCount The exact number of rows expected in `blocked_call_log` for this number.
     * @param phoneNumber E.164 number whose log entries are counted (e.g. "+12025550182").
     */
    @Then("the blocked-call log contains {int} entry for {string}")
    fun blockedCallLogContainsEntry(expectedCount: Int, phoneNumber: String) {
        val actual = adb.queryBlockedCallLogCount(phoneNumber)
        assertEquals(expectedCount, actual) {
            "Expected $expectedCount blocked-call log entry(ies) for $phoneNumber " +
                "but the database shows $actual."
        }
        log.info("Then: blocked_call_log confirmed $actual entry(ies) for $phoneNumber")
    }

    /**
     * Asserts that the most recent `blocked_call_log` entry for [phoneNumber] has the
     * [expectedReason] value in its `reason` column.
     *
     * The reason is stored as the name of a [com.callbloqued.sift.domain.model.BlockReason]
     * enum value (e.g. `"ATTEMPT_THRESHOLD"`). Using the enum name rather than its ordinal
     * means the assertion remains stable even if new [BlockReason] values are inserted in
     * later phases (F3 manual blacklist, etc.).
     *
     * @param phoneNumber E.164 number whose most recent log entry is inspected.
     * @param expectedReason The expected [com.callbloqued.sift.domain.model.BlockReason] name
     *   string (e.g. `"ATTEMPT_THRESHOLD"`).
     */
    @Then("the log entry reason for {string} is {string}")
    fun logEntryReasonIs(phoneNumber: String, expectedReason: String) {
        val actual = adb.queryBlockedCallLogReason(phoneNumber)
        assertEquals(expectedReason, actual) {
            "Expected blocked_call_log reason for $phoneNumber to be '$expectedReason' " +
                "but the database returned '$actual'."
        }
        log.info("Then: blocked_call_log reason for $phoneNumber confirmed as '$actual'")
    }

    /**
     * Marks the Then step of the history-screen scenario as pending.
     *
     * This step is never reached in practice because the [When] step
     * [userOpensCallHistoryScreen] always throws [PendingException] before execution
     * reaches here. It is declared so that Cucumber can parse and validate the feature
     * file without an "undefined step" error, and to document what the step would verify
     * once the F4 history screen is built.
     *
     * **To implement in F4**: use [io.appium.java_client.android.AndroidDriver] to find a
     * list item (RecyclerView or LazyColumn node) whose text or content-description contains
     * [phoneNumber] and assert that it is visible on screen.
     *
     * @param phoneNumber E.164 number expected to appear in the history screen.
     * @throws PendingException always.
     */
    @Then("the history screen displays a blocked-call entry for {string}")
    fun historyScreenDisplaysEntry(phoneNumber: String): Nothing {
        throw PendingException(
            "The call history screen has not been implemented yet (planned for F4). " +
                "This step is unreachable while the preceding When step throws PendingException."
        )
    }
}
