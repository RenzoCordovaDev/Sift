@F3 @ManualLists
Feature: F3 manual lists — blacklist and whitelist screening decisions

  F3 adds a manual_list Room table consulted by EvaluateIncomingCallUseCase between
  the device-contacts check (rule 3) and the attempt-threshold check (rule 6). Numbers
  in the user's blacklist are blocked on EVERY call with reason MANUAL_BLACKLIST,
  bypassing the attempt-threshold logic. Numbers in the user's whitelist are always
  allowed through, also bypassing the threshold logic.

  There is no list management UI in F3 — that screen is planned for F4. Database
  pre-conditions are established by inserting rows directly via "adb shell run-as ...
  sqlite3", the same technique used in F1 for call_attempts and in F2 for blocked_call_log.

  Numbers used in these scenarios are in the NANP 555-01xx fictional range reserved
  for testing (CLAUDE.md §10: never use real phone numbers in tests):
    +12025550142 — the "blacklisted caller" number
    +12025550151 — the "whitelisted caller" number
    +12025550160 — the "contact also in blacklist" number (non-negotiable rule 1 test)

  The @Before hook in Hooks.kt performs the following setup before EVERY scenario:
    1. adb shell pm clear com.callbloqued.sift      — wipe all app data + settings
    2. adb shell pm grant ... READ_CONTACTS          — restore runtime permission
    3. adb shell cmd role add-role-holder ...        — restore ROLE_CALL_SCREENING
    4. Launch Sift via Appium / activateApp          — let Room initialise the DB

  ─────────────────────────────────────────────────────────────────────────────────────

  @ManualBlacklisted @FirstAttempt
  Scenario: Number in manual blacklist is blocked silently on first call

    A number explicitly placed in the user's blacklist must be rejected immediately,
    even on its first call and without being in the device contacts. Unlike the default
    attempt-threshold flow (which waits for N retries), the blacklist bypasses the
    threshold entirely. The block is logged with reason MANUAL_BLACKLIST in
    blocked_call_log. The call_attempts counter is NOT incremented for blacklisted
    numbers because the retry-threshold semantic does not apply to explicitly blocked
    callers.

    Default settings after pm clear: filter enabled = true, required attempts = 1.

    Given "+12025550142" is not in the device address book
    And "+12025550142" is in the manual blacklist
    When an incoming call arrives from "+12025550142"
    Then the call does not ring on the device
    And the blocked-call log contains 1 entry for "+12025550142"
    And the log entry reason for "+12025550142" is "MANUAL_BLACKLIST"
    And no call-attempt record exists in the database for "+12025550142"

  ─────────────────────────────────────────────────────────────────────────────────────

  @ManualWhitelisted @FirstAttempt
  Scenario: Number in manual whitelist is allowed through on first call

    A number in the user's whitelist bypasses the attempt-threshold check and is allowed
    immediately on its first call, even without appearing in the device contacts.
    EvaluateIncomingCallUseCase returns Allow before reaching the threshold logic, so no
    block event is logged and no attempt counter is incremented.

    Default settings after pm clear: filter enabled = true, required attempts = 1.

    Given "+12025550151" is not in the device address book
    And "+12025550151" is in the manual whitelist
    When an incoming call arrives from "+12025550151"
    Then the call rings on the device
    And no call-attempt record exists in the database for "+12025550151"

  ─────────────────────────────────────────────────────────────────────────────────────

  @KnownContactOverridesBlacklist
  Scenario: Device contact is never blocked even when also in the manual blacklist

    Non-negotiable rule 1 (CLAUDE.md §1): a number present in the device contacts must
    ALWAYS be allowed, regardless of any other list membership. EvaluateIncomingCallUseCase
    checks device contacts BEFORE the manual blacklist (step 3 before step 4 in the
    decision flow documented in the class-level KDoc), so a blacklisted number that is
    also a contact short-circuits to Allow and never reaches the blacklist check.

    This scenario is fully automatable in this stack: the device contact is inserted via
    the same ADB ContentProvider path used in the @KnownContact scenario in F1, and the
    blacklist entry is inserted directly via sqlite3 — the only available mechanism before
    the F4 list management screen. Both insertions are established in the Given phase so
    that the When step exercises the combined state in one simulated call.

    Given the contact "+12025550160" exists in the device address book
    And "+12025550160" is in the manual blacklist
    When an incoming call arrives from "+12025550160"
    Then the call rings on the device
    And no call-attempt record exists in the database for "+12025550160"

  ─────────────────────────────────────────────────────────────────────────────────────

  @ListManagementScreen @PendingUI
  Scenario: User can add a number to the blacklist from the manual lists management screen

    The list management screen (planned for F4) will let users add and remove numbers
    from the blacklist and whitelist directly in the Sift UI, without ADB commands. This
    scenario describes the complete user-facing flow: opening the management screen,
    adding a number to the blacklist, and confirming the entry appears in the list.

    There is no list management Compose screen in F3; this scenario is marked @PendingUI
    and will be fully automated in F4 when the screen is implemented, following the same
    pattern as @FilterDisabled @PendingUI in f1_call_screening_core.feature.

    Given "+12025550142" is not in the device address book
    When the user opens the manual lists management screen
    Then the manual lists screen shows the blacklist and whitelist entries
