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
 * Cucumber step definitions for the F3 manual-lists E2E scenarios.
 *
 * F3 adds the `manual_list` Room table, which is consulted by
 * [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase] between the device-contacts
 * check and the attempt-threshold check. These steps pre-populate `manual_list` rows directly
 * via [AdbHelper] (using `adb shell run-as <pkg> sqlite3`) and assert the resulting call
 * outcome through the same mechanisms established in F1 and F2.
 *
 * **Verification strategy**: the host-side JVM test module has no direct access to Room APIs.
 * Database pre-conditions are written via `adb shell run-as <pkg> sqlite3`, exactly as F1
 * uses for `call_attempts` and F2 uses for `blocked_call_log`. Assertions after the simulated
 * call reuse existing steps from [CallScreeningSteps] (call ringing/not-ringing, attempt count)
 * and [AttemptHistorySteps] (blocked_call_log count and reason).
 *
 * **Contact-override scenario**: the [knownContactOverridesBlacklist] scenario exercises
 * non-negotiable rule 1 (CLAUDE.md §1) end-to-end by combining [AdbHelper.insertContact] (used
 * in F1) with [AdbHelper.insertManualListEntry] (added in F3). The `manual_list` entry for a
 * device contact is never reached in [EvaluateIncomingCallUseCase] because contacts are checked
 * first (step 3 before step 4 in the decision flow), so the call rings through.
 *
 * **Pending scenario**: the `@ListManagementScreen @PendingUI` scenario depends on the list
 * management Compose screen, which is planned for F4. The first UI-dependent [When] step throws
 * [PendingException], following the same pattern as `@FilterDisabled @PendingUI` in
 * `f1_call_screening_core.feature` and `@HistoryScreen @PendingUI` in `f2_attempt_history.feature`.
 *
 * @param context Scenario-scoped state injected by PicoContainer (same instance as Hooks).
 * @param adb ADB command helper injected by PicoContainer.
 */
class ManualListsSteps(
    private val context: ScenarioContext,
    private val adb: AdbHelper
) {

    private val log: Logger = Logger.getLogger(ManualListsSteps::class.java.name)

    // ─── Given steps ─────────────────────────────────────────────────────────

    /**
     * Pre-populates the `manual_list` table with a BLACKLIST entry for [phoneNumber].
     *
     * Inserts a row via [AdbHelper.insertManualListEntry] and verifies the write by reading
     * the `list_type` back via [AdbHelper.queryManualListType]. The verification mirrors the
     * pattern used in [CallScreeningSteps.databaseHasBlockedAttempts] for `call_attempts`.
     *
     * This step corresponds to the "And ... is in the manual blacklist" lines in the F3
     * feature file. Cucumber resolves "And" to the nearest preceding keyword (Given), so
     * the `@Given` annotation matches correctly even when the feature file uses "And".
     *
     * @param phoneNumber E.164 number to place in the blacklist (e.g. `"+12025550142"`).
     */
    @Given("{string} is in the manual blacklist")
    fun numberIsInManualBlacklist(phoneNumber: String) {
        adb.insertManualListEntry(phoneNumber, BLACKLIST_TYPE)
        val storedType = adb.queryManualListType(phoneNumber)
        assertEquals(BLACKLIST_TYPE, storedType) {
            "Expected manual_list to contain $phoneNumber with list_type='$BLACKLIST_TYPE' " +
                "but the database returned '$storedType'. " +
                "Ensure the app was launched by the @Before hook so the DB schema exists."
        }
        log.info("Given: $phoneNumber inserted into manual blacklist (list_type=$storedType)")
    }

    /**
     * Pre-populates the `manual_list` table with a WHITELIST entry for [phoneNumber].
     *
     * Inserts a row via [AdbHelper.insertManualListEntry] and verifies the write by reading
     * the `list_type` back via [AdbHelper.queryManualListType].
     *
     * @param phoneNumber E.164 number to place in the whitelist (e.g. `"+12025550151"`).
     */
    @Given("{string} is in the manual whitelist")
    fun numberIsInManualWhitelist(phoneNumber: String) {
        adb.insertManualListEntry(phoneNumber, WHITELIST_TYPE)
        val storedType = adb.queryManualListType(phoneNumber)
        assertEquals(WHITELIST_TYPE, storedType) {
            "Expected manual_list to contain $phoneNumber with list_type='$WHITELIST_TYPE' " +
                "but the database returned '$storedType'. " +
                "Ensure the app was launched by the @Before hook so the DB schema exists."
        }
        log.info("Given: $phoneNumber inserted into manual whitelist (list_type=$storedType)")
    }

    // ─── When steps ──────────────────────────────────────────────────────────

    /**
     * Marks the scenario as pending because the manual lists management screen is not yet
     * implemented.
     *
     * The screen will be delivered in F4 (UI phase). There is no Appium path to navigate to
     * a screen that does not exist in the app. Until the list management Compose screen is
     * available this step throws [PendingException], causing the `@ListManagementScreen
     * @PendingUI` scenario to appear as PENDING rather than FAILED in the Cucumber report.
     *
     * **To implement this step in F4**: replace the [PendingException] with Appium navigation
     * logic — e.g. tap the "Lists" bottom-nav item or navigate via the host Intent — and
     * complete the corresponding [manualListsScreenShowsEntries] Then step.
     *
     * @throws PendingException always — marks the scenario as pending in the Cucumber report.
     */
    @When("the user opens the manual lists management screen")
    fun userOpensManualListsScreen(): Nothing {
        throw PendingException(
            "The manual lists management screen has not been implemented yet (planned for F4). " +
                "This scenario cannot be automated until the list management Compose screen exists."
        )
    }

    // ─── Then steps ──────────────────────────────────────────────────────────

    /**
     * Marks the Then step of the list-management scenario as pending.
     *
     * This step is never reached in practice because [userOpensManualListsScreen] always
     * throws [PendingException] before execution reaches here. It is declared so that Cucumber
     * can parse and validate the feature file without an "undefined step" error, and to document
     * what the step would verify once the F4 management screen is built.
     *
     * **To implement in F4**: use [io.appium.java_client.android.AndroidDriver] to assert that
     * the management screen's blacklist and whitelist sections are visible (e.g. find composable
     * nodes whose content-description or text matches the list labels).
     *
     * @throws PendingException always.
     */
    @Then("the manual lists screen shows the blacklist and whitelist entries")
    fun manualListsScreenShowsEntries(): Nothing {
        throw PendingException(
            "The manual lists management screen has not been implemented yet (planned for F4). " +
                "This step is unreachable while the preceding When step throws PendingException."
        )
    }

    companion object {
        /**
         * [com.callbloqued.sift.domain.model.ManualListType.BLACKLIST] enum name as stored in
         * the `manual_list.list_type` column. Must match the exact string used by Room.
         */
        private const val BLACKLIST_TYPE = "BLACKLIST"

        /**
         * [com.callbloqued.sift.domain.model.ManualListType.WHITELIST] enum name as stored in
         * the `manual_list.list_type` column. Must match the exact string used by Room.
         */
        private const val WHITELIST_TYPE = "WHITELIST"
    }
}
