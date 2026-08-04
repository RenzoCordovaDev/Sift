@F1 @CallScreening
Feature: F1 call screening core — end-to-end decision flow

  Sift intercepts incoming calls through IncomingCallScreeningService and delegates the
  allow/block decision to EvaluateIncomingCallUseCase. These scenarios validate the
  complete path from a simulated ADB call on the emulator to the observable outcome
  on the device (ringing or silent block) and in the local database (attempt record).

  Numbers used in these scenarios are in the NANP 555-01xx fictional range reserved
  for testing (CLAUDE.md §10: never use real phone numbers in tests):
    +12025550173 — the "known contact" number
    +12025550182 — the "unknown caller" number
    +12025550191 — the "filter-disabled" number (scenario pending F4 UI)

  The @Before hook in Hooks.kt performs the following setup before EVERY scenario:
    1. adb shell pm clear com.callbloqued.sift      — wipe all app data + settings
    2. adb shell pm grant ... READ_CONTACTS          — restore runtime permission
    3. adb shell cmd role add-role-holder ...        — restore ROLE_CALL_SCREENING
    4. Launch Sift via Appium / activateApp          — let Room initialise the DB

  ─────────────────────────────────────────────────────────────────────────────────────

  @KnownContact
  Scenario: Known device contact always rings through regardless of prior call history

    Known contacts must NEVER be blocked (CLAUDE.md non-negotiable rule 1).
    The use case short-circuits at the ContactsRepository check and returns Allow
    before ever consulting the attempt history.

    Given the contact "+12025550173" exists in the device address book
    When an incoming call arrives from "+12025550173"
    Then the call rings on the device
    And no call-attempt record exists in the database for "+12025550173"

  ─────────────────────────────────────────────────────────────────────────────────────

  @UnknownNumber @FirstAttempt
  Scenario: First call from an unknown number is blocked silently

    An unrecognised caller on their first attempt must be silently discarded
    (setDisallowCall=true, setRejectCall=false — no ring, no rejection tone to the caller)
    and the attempt must be recorded so that the threshold check works on the second call.

    Default settings after pm clear: filter enabled = true, required attempts = 1.

    Given "+12025550182" is not in the device address book
    When an incoming call arrives from "+12025550182"
    Then the call does not ring on the device
    And the database shows 1 blocked attempt for "+12025550182"

  ─────────────────────────────────────────────────────────────────────────────────────

  @UnknownNumber @SecondAttempt
  Scenario: Second call from the same unknown number is allowed through

    After one prior blocked attempt (the default threshold), the next call from the
    same number must be allowed. This tests that EvaluateIncomingCallUseCase reads the
    stored count, compares it against the threshold, and returns Allow.

    Default threshold: 1 required attempt before allowing (SettingsDataStore default).
    The prior attempt is pre-populated directly into the Room database via ADB so that
    this scenario tests ONLY the "second attempt → allow" branch in isolation.

    Given "+12025550182" is not in the device address book
    And the database already has 1 blocked attempt recorded for "+12025550182"
    When an incoming call arrives from "+12025550182"
    Then the call rings on the device

  ─────────────────────────────────────────────────────────────────────────────────────

  @FilterDisabled @PendingUI
  Scenario: Screening filter disabled allows every call through without blocking

    When the user turns off the screening filter, every call must ring regardless of
    whether the number is unknown. The filter toggle is a DataStore setting
    (SettingsDataStore.KEY_FILTER_ENABLED). There is no Settings UI in F1; this
    scenario is marked @PendingUI and will be fully implemented in F4 when the
    Settings screen is available.

    Given the call-screening filter is disabled
    And "+12025550191" is not in the device address book
    When an incoming call arrives from "+12025550191"
    Then the call rings on the device
    And no call-attempt record exists in the database for "+12025550191"
