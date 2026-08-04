package support

import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import java.util.logging.Logger

/**
 * Cucumber lifecycle hooks that run before and after every scenario.
 *
 * PicoContainer injects [context] and [adb] as constructor parameters, making them
 * the same instances that the step-definition classes receive.
 *
 * **Setup order** (executed once per scenario, before any step):
 * 1. Clear all app data via `pm clear` — wipes Room DB, DataStore, and runtime permissions.
 * 2. Re-grant [android.Manifest.permission.READ_CONTACTS] — needed by ContactsRepositoryImpl.
 * 3. Re-grant [android.app.role.CALL_SCREENING] — needed by IncomingCallScreeningService.
 * 4. Create a new Appium session → launches Sift's MainActivity.
 * 5. Wait for the app and Room to initialise (2 s; Room's DB builder is synchronous in Hilt
 *    SingletonComponent so the DB file exists after MainActivity.onCreate returns).
 *
 * **Teardown order** (executed once per scenario, after all steps even on failure):
 * 1. Cancel any simulated call still in progress.
 * 2. Delete test contacts inserted by the scenario.
 * 3. Quit the Appium session.
 *
 * @param context Scenario-scoped state shared with step-definition classes.
 * @param adb Helper for executing ADB commands against the connected emulator.
 */
class Hooks(
    private val context: ScenarioContext,
    private val adb: AdbHelper
) {

    private val log: Logger = Logger.getLogger(Hooks::class.java.name)

    /**
     * Prepares a clean device state and starts an Appium session for the scenario.
     *
     * Scenarios tagged with [@PendingUI][io.cucumber.java.PendingException] still receive
     * this setup; the first step of those scenarios throws
     * [io.cucumber.java.PendingException] immediately, and teardown runs normally.
     *
     * @param scenario Cucumber scenario metadata (used for logging).
     */
    @Before(order = 0)
    fun setUp(scenario: Scenario) {
        log.info("=== SETUP: ${scenario.name} ===")

        adb.clearAppData()
        adb.grantReadContactsPermission()
        adb.grantCallScreeningRole()

        context.driver = AppiumDriverFactory.create()

        // Give MainActivity and Hilt DI time to complete so Room creates sift_database
        // before any step tries to insert pre-conditions into it.
        Thread.sleep(APP_READY_WAIT_MS)
        log.info("setUp complete — app ready")
    }

    /**
     * Cancels any lingering simulated call, removes test contacts, and closes the Appium
     * session. Runs even when a scenario step throws (including [io.cucumber.java.PendingException]).
     *
     * @param scenario Cucumber scenario metadata (used for logging the final status).
     */
    @After(order = 0)
    fun tearDown(scenario: Scenario) {
        log.info("=== TEARDOWN: ${scenario.name} [${scenario.status}] ===")

        context.lastSimulatedCallNumber?.let { number ->
            runCatching { adb.cancelSimulatedCall(number) }
                .onFailure { log.warning("tearDown: could not cancel call from $number: ${it.message}") }
        }

        context.insertedContactIds.forEach { rawId ->
            runCatching { adb.deleteContact(rawId) }
                .onFailure { log.warning("tearDown: could not delete contact $rawId: ${it.message}") }
        }
        context.insertedContactIds.clear()

        context.driver?.let { driver ->
            runCatching { driver.quit() }
                .onFailure { log.warning("tearDown: Appium quit failed: ${it.message}") }
        }
        context.driver = null

        log.info("tearDown complete")
    }

    companion object {
        /**
         * Milliseconds to wait after launching Sift before any step executes.
         *
         * Room's database builder is synchronous; Hilt's SingletonComponent eagerly
         * initialises AppDatabase when the app process starts. Two seconds is sufficient
         * for a cold start on a standard AVD; increase if the emulator is under load.
         */
        private const val APP_READY_WAIT_MS = 2_000L
    }
}
