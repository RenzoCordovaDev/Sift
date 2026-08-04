package com.callbloqued.sift.domain.model

/**
 * Describes why an incoming call was blocked silently by the screening service.
 *
 * This enum is designed to be extended across phases without breaking existing
 * persisted records: values are stored by name in the database, so adding a new
 * entry in a later phase does not require a schema migration.
 *
 * **Current values (F2):**
 * - [ATTEMPT_THRESHOLD] — the caller has not yet reached the configured retry threshold.
 *
 * **Planned values (F3):**
 * - `MANUAL_BLACKLIST` — the caller's number was explicitly added to the user's blacklist.
 */
enum class BlockReason {

    /**
     * The call was blocked because the caller's attempt count is below the user-configured
     * threshold ([com.callbloqued.sift.domain.repository.SettingsRepository.getRequiredAttemptCount]).
     * This is the only reason available in F1/F2. Real people who call back will be allowed
     * through once the threshold is reached.
     */
    ATTEMPT_THRESHOLD
}
