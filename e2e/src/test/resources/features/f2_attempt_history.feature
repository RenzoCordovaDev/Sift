@F2 @AttemptHistory
Feature: F2 attempt history — blocked-call event log

  F2 adds a blocked_call_log Room table alongside the existing call_attempts aggregate.
  While call_attempts stores one row per caller (updated on every block for the "allow
  on Nth attempt" decision logic), blocked_call_log records each individual silent-block
  event — one new row per blocked call. This log is the data source for the user-visible
  call history screen that will be delivered in F4.

  What changes end-to-end in F2:
    EvaluateIncomingCallUseCase.invoke calls recordAndLogBlock on a blocking decision,
    which now issues both callAttemptRepository.recordAttempt AND
    callLogRepository.logBlockedCall. The second write persists a BlockedCallLogEntity
    row with the caller's number, a millisecond-precision timestamp, and a BlockReason
    name string (e.g. "ATTEMPT_THRESHOLD").

  Numbers used in these scenarios are in the NANP 555-01xx fictional range reserved
  for testing (CLAUDE.md §10: never use real phone numbers in tests):
    +12025550182 — the "unknown caller" number

  The @Before hook in Hooks.kt performs the following setup before EVERY scenario:
    1. adb shell pm clear com.callbloqued.sift      — wipe all app data + settings
    2. adb shell pm grant ... READ_CONTACTS          — restore runtime permission
    3. adb shell cmd role add-role-holder ...        — restore ROLE_CALL_SCREENING
    4. Launch Sift via Appium / activateApp          — let Room initialise the DB

  ─────────────────────────────────────────────────────────────────────────────────────

  @BlockedCallLogged
  Scenario: Blocked call is recorded as an event in the blocked-call log

    When Sift silently discards a first-time call from an unknown number,
    EvaluateIncomingCallUseCase must write exactly one row to blocked_call_log with
    the caller's phone number and reason ATTEMPT_THRESHOLD.

    The blocked_call_log table is separate from call_attempts: call_attempts tracks the
    aggregate count per caller (used for the "allow on Nth attempt" decision), while
    blocked_call_log records each individual block event for user-visible history.

    Verification is performed by querying the Room SQLite database directly via
    "adb shell run-as <pkg> sqlite3" — without a history UI, the database is the only
    observable ground truth for whether the event was persisted correctly.

    Default settings after pm clear: filter enabled = true, required attempts = 1.

    Given "+12025550182" is not in the device address book
    When an incoming call arrives from "+12025550182"
    Then the call does not ring on the device
    And the blocked-call log contains 1 entry for "+12025550182"
    And the log entry reason for "+12025550182" is "ATTEMPT_THRESHOLD"

  ─────────────────────────────────────────────────────────────────────────────────────

  @HistoryScreen @PendingUI
  Scenario: Call history screen displays the blocked-call event log

    The F4 history screen must display the entries from blocked_call_log in
    chronological order (most recent first), so the user can review which unknown callers
    were silently blocked and when.

    This scenario cannot be automated until F4 delivers the history Compose screen.
    There is no Appium path to a screen that does not exist yet. The underlying data
    layer (insertion and retrieval) is covered by the @BlockedCallLogged scenario above
    and by the F2 unit tests for CallLogRepositoryImpl.

    The pattern mirrors @FilterDisabled @PendingUI in f1_call_screening_core.feature:
    the first UI-dependent step throws PendingException and the scenario appears as
    PENDING in the Cucumber report rather than FAILED.

    Given "+12025550182" is not in the device address book
    And a blocked call from "+12025550182" was previously logged
    When the user opens the call history screen
    Then the history screen displays a blocked-call entry for "+12025550182"
