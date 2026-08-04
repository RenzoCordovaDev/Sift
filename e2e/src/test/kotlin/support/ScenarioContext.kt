package support

import io.appium.java_client.android.AndroidDriver

/**
 * PicoContainer-managed context shared between [support.Hooks] and all step-definition classes.
 *
 * Cucumber-PicoContainer creates exactly one instance of this class per scenario and injects it
 * into every class that declares it as a constructor parameter, giving steps and hooks a clean
 * shared mutable state without static fields.
 *
 * Fields are intentionally `var` (not `val`) so Hooks can assign the driver after Appium
 * session creation, and step definitions can read it during scenario execution.
 */
class ScenarioContext {

    /**
     * Active Appium [AndroidDriver] session for the current scenario.
     *
     * Set by [Hooks.setUp] after the Appium session is created; nullified by [Hooks.tearDown]
     * after the session is quit. Step definitions must treat this as non-null during scenario
     * execution (the setup hook guarantees it is populated before any step runs).
     */
    var driver: AndroidDriver? = null

    /**
     * Raw-contact IDs of contacts inserted into the device address book during the current
     * scenario. Populated by the "contact exists in device address book" step; read by
     * [Hooks.tearDown] to delete the contacts after the scenario completes.
     */
    val insertedContactIds: MutableList<String> = mutableListOf()

    /**
     * Phone number of the most recently simulated incoming call, kept so [Hooks.tearDown] can
     * cancel any call that was not already ended by a step definition.
     */
    var lastSimulatedCallNumber: String? = null
}
