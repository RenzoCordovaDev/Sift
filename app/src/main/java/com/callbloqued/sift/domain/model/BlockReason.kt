package com.callbloqued.sift.domain.model

/**
 * Describes why an incoming call was blocked silently by the screening service.
 *
 * This enum is designed to be extended across phases without breaking existing
 * persisted records: values are stored by name in the database, so adding a new
 * entry in a later phase does not require a schema migration.
 *
 * **Current values (F3):**
 * - [ATTEMPT_THRESHOLD] — the caller has not yet reached the configured retry threshold.
 * - [MANUAL_BLACKLIST] — the caller's number was explicitly added to the user's blacklist.
 */
enum class BlockReason {

    /**
     * The call was blocked because the caller's attempt count is below the user-configured
     * threshold ([com.callbloqued.sift.domain.repository.SettingsRepository.getRequiredAttemptCount]).
     * Real people who call back will be allowed through once the threshold is reached.
     */
    ATTEMPT_THRESHOLD,

    /**
     * The call was blocked because the caller's number was explicitly added to the user's
     * manual blacklist ([com.callbloqued.sift.domain.model.ManualListType.BLACKLIST]).
     * The attempt-threshold logic is bypassed entirely for blacklisted numbers; they are
     * rejected on every call unless present in the device contacts.
     */
    MANUAL_BLACKLIST
}
