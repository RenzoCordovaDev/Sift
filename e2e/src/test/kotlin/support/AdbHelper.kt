package support

import java.util.logging.Logger

/**
 * Thin wrapper around ADB shell and emulator-console commands used by the Sift E2E test suite.
 *
 * All methods execute `adb` (or the serial-qualified variant if the `adb.serial` system
 * property is set) as a child process on the HOST machine. The emulator must already be
 * connected and the ADB binary must be on the system PATH.
 *
 * The emulator serial is read from the `adb.serial` system property at construction time,
 * which Gradle propagates from `-Dadb.serial=emulator-5554` command-line flags (see
 * `e2e/build.gradle.kts`). When the property is absent or empty, ADB uses the single
 * connected device, which is the typical case during local development.
 *
 * **Quoting convention for `adb shell` commands**: ADB joins all arguments after `shell`
 * into a single string and sends it to the device's `/bin/sh`. SQL queries are wrapped in
 * shell double quotes so that single-quote SQL string delimiters survive the device shell
 * without being consumed as POSIX quoting characters.
 *
 * This class has no constructor parameters so PicoContainer can instantiate it directly
 * and inject it into [support.Hooks] and [steps.CallScreeningSteps].
 */
class AdbHelper {

    private val adbSerial: String = System.getProperty("adb.serial", "")

    private val log: Logger = Logger.getLogger(AdbHelper::class.java.name)

    // ─── App lifecycle ────────────────────────────────────────────────────────

    /**
     * Clears all data for [APP_PACKAGE] (equivalent to "Clear Storage" in Android settings).
     *
     * Wipes the Room database, DataStore preferences, and any other files in the app's
     * private storage. After this call the app behaves as if freshly installed. Call this
     * before each scenario to guarantee test isolation.
     *
     * The app must be re-launched after clearing data so Room can re-initialise the schema.
     */
    fun clearAppData() {
        exec("shell", "pm", "clear", APP_PACKAGE)
    }

    // ─── Permissions and roles ────────────────────────────────────────────────

    /**
     * Grants [android.Manifest.permission.READ_CONTACTS] to [APP_PACKAGE] at the ADB shell
     * level, bypassing the runtime-permission dialog.
     *
     * `pm clear` revokes all runtime permissions, so this must be called after every
     * [clearAppData] invocation. Without this permission [ContactsRepositoryImpl] fails open
     * (treats every caller as a known contact), making all calls pass through — which would
     * silently invalidate the blocking assertions.
     */
    fun grantReadContactsPermission() {
        exec("shell", "pm", "grant", APP_PACKAGE, "android.permission.READ_CONTACTS")
    }

    /**
     * Assigns the `CALL_SCREENING` role to [APP_PACKAGE] via the `role` system service.
     *
     * This is the equivalent of the user selecting Sift in Settings → Apps → Default apps →
     * Caller ID & spam, which is normally done during F5 onboarding. Granting the role
     * ensures [IncomingCallScreeningService] is bound by the Android telecom stack when an
     * incoming call arrives.
     *
     * Requires Android 10+ (API 29+) — the project's minimum SDK guarantees this.
     *
     * @param userId Android user ID on the emulator. Defaults to 0 (the primary user).
     */
    fun grantCallScreeningRole(userId: Int = 0) {
        exec(
            "shell", "cmd", "role", "add-role-holder",
            "android.app.role.CALL_SCREENING",
            APP_PACKAGE,
            userId.toString()
        )
    }

    // ─── Contacts ─────────────────────────────────────────────────────────────

    /**
     * Inserts a minimal contact into the device address book with [phoneNumber] as its only
     * phone data. Uses the `content` ADB subcommand against `ContactsContract` URIs.
     *
     * Insertion is a two-step operation:
     *   1. Create a raw_contact row (returns the new row URI containing the raw_contact ID).
     *   2. Attach the phone number to that raw_contact via a `data` row.
     *
     * @param phoneNumber Phone number to store in E.164 format (e.g. "+12025550173").
     * @return The raw_contact ID string as returned by ContactsContract, or an empty string
     *   if the insertion failed.
     */
    fun insertContact(phoneNumber: String): String {
        val rawContactOutput = exec(
            "shell", "content", "insert",
            "--uri", "content://com.android.contacts/raw_contacts",
            "--bind", "account_type:s:",
            "--bind", "account_name:s:"
        )
        // Output format: "result=content://com.android.contacts/raw_contacts/<ID>"
        val rawContactId = rawContactOutput.substringAfterLast('/').trim()
        if (rawContactId.isBlank() || rawContactId.toLongOrNull() == null) {
            log.warning("insertContact: unexpected output from raw_contacts insert: '$rawContactOutput'")
            return ""
        }

        exec(
            "shell", "content", "insert",
            "--uri", "content://com.android.contacts/data",
            "--bind", "raw_contact_id:i:$rawContactId",
            "--bind", "mimetype:s:vnd.android.cursor.item/phone_v2",
            "--bind", "data1:s:$phoneNumber",
            "--bind", "data2:i:2"  // type = TYPE_MOBILE
        )

        log.info("insertContact: inserted $phoneNumber with raw_contact_id=$rawContactId")
        return rawContactId
    }

    /**
     * Deletes the raw_contact identified by [rawContactId] and its associated data rows.
     *
     * ContactsContract cascades the delete from raw_contacts to data rows automatically.
     *
     * @param rawContactId The raw_contact ID string returned by [insertContact].
     */
    fun deleteContact(rawContactId: String) {
        if (rawContactId.isBlank()) return
        exec(
            "shell", "content", "delete",
            "--uri", "content://com.android.contacts/raw_contacts/$rawContactId"
        )
        log.info("deleteContact: deleted raw_contact_id=$rawContactId")
    }

    // ─── Simulated incoming calls ─────────────────────────────────────────────

    /**
     * Simulates an incoming call from [phoneNumber] using the Android emulator's GSM console.
     *
     * This command is equivalent to the user receiving a real phone call and is the only
     * reliable way to trigger [IncomingCallScreeningService.onScreenCall] from outside the
     * Android process boundary. Requires a running Android Virtual Device (AVD); it does NOT
     * work on physical devices.
     *
     * @param phoneNumber Caller number sent to the emulator console (e.g. "+12025550182").
     */
    fun simulateIncomingCall(phoneNumber: String) {
        exec("emu", "gsm", "call", phoneNumber)
        log.info("simulateIncomingCall: simulated call from $phoneNumber")
    }

    /**
     * Cancels a previously simulated call from [phoneNumber].
     *
     * Should be called in teardown to ensure the emulator is not left in a ringing state
     * between scenarios, which would interfere with subsequent call detections.
     *
     * @param phoneNumber The same number passed to [simulateIncomingCall].
     */
    fun cancelSimulatedCall(phoneNumber: String) {
        exec("emu", "gsm", "cancel", phoneNumber)
        log.info("cancelSimulatedCall: cancelled call from $phoneNumber")
    }

    // ─── Database inspection ──────────────────────────────────────────────────

    /**
     * Returns the number of device contacts that have [phoneNumber] as a phone data row.
     *
     * Queries `ContactsContract.Data` for rows with `mimetype = phone_v2` and `data1 = phoneNumber`
     * via `adb shell content query`. A result of 0 confirms the number is absent from the
     * address book, which is the pre-condition for the "unknown caller" scenarios.
     *
     * @param phoneNumber E.164 phone number to look up (e.g. "+12025550182").
     * @return The number of matching contact data rows; 0 if none found.
     */
    fun queryContactCount(phoneNumber: String): Int {
        val output = exec(
            "shell", "content", "query",
            "--uri", "content://com.android.contacts/data",
            "--projection", "data1",
            "--where", "mimetype='vnd.android.cursor.item/phone_v2' AND data1='$phoneNumber'"
        )
        // content query prints "Row: 0 data1=..." for each result or "No result found." when empty.
        return if (output.contains("No result found", ignoreCase = true) || output.isBlank()) {
            0
        } else {
            output.lines().count { it.trimStart().startsWith("Row:") }
        }
    }

    /**
     * Returns the `attempt_count` stored in the `call_attempts` table for [phoneNumber].
     *
     * Queries the Room SQLite database directly via `run-as` + `sqlite3`. This bypasses the
     * Android app layer entirely and reads the raw persisted state, making it a reliable
     * ground-truth assertion for the E2E scenarios.
     *
     * **Quoting note**: the SQL is wrapped in shell double quotes so that the single-quote
     * SQL string delimiter around [phoneNumber] survives `/bin/sh` without being stripped.
     *
     * Prerequisites:
     * - The APK must be a `debuggable` build (true for `debug` variant).
     * - `sqlite3` must be available at `/system/bin/sqlite3` on the emulator image
     *   (true for standard AOSP-based AVDs; may be absent on some manufacturer images).
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+12025550182").
     * @return The stored `attempt_count`, or 0 if no record exists or the query fails.
     */
    fun queryAttemptCount(phoneNumber: String): Int {
        val sql = "SELECT COALESCE(attempt_count,0) FROM call_attempts WHERE phone_number='$phoneNumber'"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        val result = execShell(shellCmd).trim()
        return result.toIntOrNull().also {
            if (it == null) log.warning("queryAttemptCount: non-integer output for $phoneNumber: '$result'")
        } ?: 0
    }

    /**
     * Pre-populates the `call_attempts` table with a row for [phoneNumber] so that a
     * scenario can start with a known history state without having to simulate a prior call.
     *
     * Uses `INSERT OR REPLACE` so the call is idempotent in case the row already exists
     * from a previous (failed/re-run) scenario.
     *
     * Prerequisites: [clearAppData] and app launch must have already run so the Room schema
     * (including the `call_attempts` table) exists in the database file.
     *
     * @param phoneNumber Phone number in E.164 format.
     * @param attemptCount The `attempt_count` value to pre-populate.
     */
    fun insertCallAttempt(phoneNumber: String, attemptCount: Int) {
        val now = System.currentTimeMillis()
        val sql = "INSERT OR REPLACE INTO call_attempts" +
            "(phone_number,first_attempt_at,last_attempt_at,attempt_count)" +
            " VALUES('$phoneNumber',$now,$now,$attemptCount)"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        execShell(shellCmd)
        log.info("insertCallAttempt: inserted $phoneNumber with count=$attemptCount")
    }

    // ─── Blocked-call event log ───────────────────────────────────────────────

    /**
     * Returns the number of rows in the `blocked_call_log` table for [phoneNumber].
     *
     * Queries the Room SQLite database directly via `run-as` + `sqlite3`. Used by F2 E2E
     * scenarios to verify that [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase]
     * persisted a blocked-call event (via [com.callbloqued.sift.domain.repository.CallLogRepository])
     * after silently rejecting a call.
     *
     * Unlike [queryAttemptCount], which reads the aggregate attempt count from `call_attempts`,
     * this method counts individual event rows in `blocked_call_log` — a distinct table added
     * in F2 that records every discrete block event for the user-visible call history.
     *
     * Prerequisites: same as [queryAttemptCount] — debuggable APK, sqlite3 on the emulator.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+12025550182").
     * @return Count of rows in `blocked_call_log` matching [phoneNumber], or 0 on error.
     */
    fun queryBlockedCallLogCount(phoneNumber: String): Int {
        val sql = "SELECT COUNT(*) FROM blocked_call_log WHERE phone_number='$phoneNumber'"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        val result = execShell(shellCmd).trim()
        return result.toIntOrNull().also {
            if (it == null) log.warning("queryBlockedCallLogCount: non-integer output for $phoneNumber: '$result'")
        } ?: 0
    }

    /**
     * Returns the `reason` value of the most recent `blocked_call_log` entry for [phoneNumber].
     *
     * The reason is stored as the name of a [com.callbloqued.sift.domain.model.BlockReason]
     * enum value (e.g. `"ATTEMPT_THRESHOLD"`). Returns an empty string if no matching row
     * exists or if the query fails.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+12025550182").
     * @return The reason string of the latest log entry, or an empty string if none found.
     */
    fun queryBlockedCallLogReason(phoneNumber: String): String {
        val sql = "SELECT reason FROM blocked_call_log" +
            " WHERE phone_number='$phoneNumber' ORDER BY timestamp DESC LIMIT 1"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        return execShell(shellCmd).trim()
    }

    /**
     * Inserts one row into the `blocked_call_log` table for [phoneNumber] with the current
     * system timestamp and the given [reason].
     *
     * Used by scenarios that need a pre-existing log entry without having to simulate a call.
     * The auto-generated `id` column is handled by Room's `AUTOINCREMENT` constraint.
     *
     * Prerequisites: [clearAppData] and app launch must have already run so the Room schema
     * (including the `blocked_call_log` table added in F2) exists in the database file.
     *
     * @param phoneNumber Phone number in E.164 format.
     * @param reason [com.callbloqued.sift.domain.model.BlockReason] enum name to store
     *   (e.g. `"ATTEMPT_THRESHOLD"`).
     */
    fun insertBlockedCallLogEntry(phoneNumber: String, reason: String = "ATTEMPT_THRESHOLD") {
        val now = System.currentTimeMillis()
        val sql = "INSERT INTO blocked_call_log(phone_number,timestamp,reason)" +
            " VALUES('$phoneNumber',$now,'$reason')"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        execShell(shellCmd)
        log.info("insertBlockedCallLogEntry: inserted log for $phoneNumber reason=$reason")
    }

    // ─── Manual list operations ───────────────────────────────────────────────

    /**
     * Inserts or replaces a row in the `manual_list` table for [phoneNumber] with the
     * given [listType] name (either `"BLACKLIST"` or `"WHITELIST"`).
     *
     * Uses `INSERT OR REPLACE` so the call is idempotent and honours the primary-key
     * mutual-exclusivity constraint of [com.callbloqued.sift.data.local.db.ManualListEntity]:
     * if [phoneNumber] was already in the opposite list it is replaced atomically.
     *
     * The [listType] string must be the exact [com.callbloqued.sift.domain.model.ManualListType]
     * enum name stored by Room (e.g. `"BLACKLIST"` not `"blacklist"`). An incorrect value
     * would insert a row that [com.callbloqued.sift.data.repository.ManualListRepositoryImpl]
     * cannot parse, silently treating the number as absent from both lists.
     *
     * Prerequisites: [clearAppData] and app launch must have already run so the Room schema
     * (including the `manual_list` table added in F3) exists in the database file.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. `"+12025550142"`).
     * @param listType [com.callbloqued.sift.domain.model.ManualListType] enum name to store
     *   (`"BLACKLIST"` or `"WHITELIST"`).
     */
    fun insertManualListEntry(phoneNumber: String, listType: String) {
        val now = System.currentTimeMillis()
        val sql = "INSERT OR REPLACE INTO manual_list(phone_number,list_type,added_at)" +
            " VALUES('$phoneNumber','$listType',$now)"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        execShell(shellCmd)
        log.info("insertManualListEntry: inserted $phoneNumber as $listType")
    }

    /**
     * Returns the `list_type` value stored in the `manual_list` table for [phoneNumber],
     * or an empty string if no row exists or the query fails.
     *
     * Used by step definitions to verify that [insertManualListEntry] persisted the row
     * correctly before the scenario proceeds to simulate a call.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. `"+12025550142"`).
     * @return The `list_type` string of the row (`"BLACKLIST"` or `"WHITELIST"`), or `""`
     *   if no matching row exists.
     */
    fun queryManualListType(phoneNumber: String): String {
        val sql = "SELECT list_type FROM manual_list WHERE phone_number='$phoneNumber' LIMIT 1"
        val shellCmd = """run-as $APP_PACKAGE sqlite3 /data/data/$APP_PACKAGE/databases/$DB_NAME "$sql""""
        return execShell(shellCmd).trim()
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Executes `adb [serial] <args>` as a child process and returns trimmed stdout.
     *
     * @param args Arguments passed after the optional `-s <serial>` device qualifier.
     * @return Combined stdout output of the command, trimmed.
     */
    private fun exec(vararg args: String): String {
        val cmd: List<String> = buildList {
            add("adb")
            if (adbSerial.isNotBlank()) {
                add("-s")
                add(adbSerial)
            }
            addAll(args)
        }
        log.fine("exec: ${cmd.joinToString(" ")}")
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            log.warning("exec exit=$exitCode cmd=${cmd.take(4).joinToString(" ")}: $output")
        }
        return output.trim()
    }

    /**
     * Executes `adb [serial] shell <shellCommand>` where [shellCommand] is passed as a
     * single argument so ADB sends the entire string to the device's `/bin/sh`.
     *
     * This single-string form is required for commands that contain shell metacharacters
     * (e.g. double quotes wrapping a SQL argument with inner single quotes). When passed as
     * a single argument, ADB forwards it verbatim to `/bin/sh -c`.
     *
     * @param shellCommand A fully formed shell command string, including any quoting.
     * @return Trimmed stdout of the command.
     */
    private fun execShell(shellCommand: String): String {
        return exec("shell", shellCommand)
    }

    companion object {
        /** Package name of the Sift app. Matches AndroidManifest and Room's DatabaseModule. */
        const val APP_PACKAGE = "com.callbloqued.sift"

        /**
         * Room database file name as set by [com.callbloqued.sift.core.di.DatabaseModule].
         * Must stay in sync with the `databaseBuilder` call in that module.
         */
        const val DB_NAME = "sift_database"
    }
}
