package com.callbloqued.sift.domain.usecase

import com.callbloqued.sift.domain.model.BlockReason
import com.callbloqued.sift.domain.model.CallDecision
import com.callbloqued.sift.domain.repository.SettingsRepository
import com.callbloqued.sift.domain.util.PhoneNumberNormalizer
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Core business logic for deciding whether an incoming call should be allowed or blocked.
 *
 * This use case is the single authority for the call-screening decision. It has no Android
 * imports and no side-effects beyond delegating reads/writes to its repository dependencies,
 * making it fully unit-testable on the JVM.
 *
 * **Decision flow (evaluated in order; first matching rule wins):**
 * 1. If the screening filter is disabled by the user → [CallDecision.Allow].
 * 2. If [rawPhoneNumber] cannot be normalised to E.164 → [CallDecision.Allow] (fail open:
 *    we cannot safely identify the caller, so we avoid a false-positive block).
 * 3. If the normalised number belongs to a device contact → [CallDecision.Allow]
 *    (non-negotiable rule: known contacts are never blocked, even if also present in the
 *    manual blacklist — contacts always win).
 * 4. If the number is in the user's manual blacklist → log the block event and return
 *    [CallDecision.DisallowSilently]. The attempt counter is not incremented for blacklisted
 *    numbers because the attempt-threshold logic is irrelevant for explicitly blocked numbers.
 * 5. If the number is in the user's manual whitelist → [CallDecision.Allow]
 *    (bypasses the attempt-threshold check entirely).
 * 6. If the number has already been blocked ≥ [SettingsRepository.getRequiredAttemptCount]
 *    times → [CallDecision.Allow] (caller has persisted; treat as legitimate).
 * 7. Otherwise → record the attempt in the aggregate store, log the block event, and
 *    return [CallDecision.DisallowSilently].
 *
 * The four data-access repositories are grouped in [ScreeningRepositories] to keep this
 * constructor within the five-parameter limit defined in CODE_QUALITY_STANDARDS.md §4.
 *
 * @param settingsRepository Used to read the filter-enabled flag and the attempt threshold.
 * @param normalizer Used to convert the raw caller number to a canonical E.164 string.
 * @param repositories Groups the four data-access repositories needed for the decision flow.
 */
class EvaluateIncomingCallUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val normalizer: PhoneNumberNormalizer,
    private val repositories: ScreeningRepositories
) {

    /**
     * Evaluates the incoming call identified by [rawPhoneNumber] and returns the action to take.
     *
     * This is a suspending operator function so callers can invoke it as
     * `evaluateIncomingCallUseCase(number)` from within a coroutine.
     *
     * Settings values ([SettingsRepository.isFilterEnabled], [SettingsRepository.getRequiredAttemptCount])
     * are read as one-shot snapshots via [kotlinx.coroutines.flow.Flow.first] so that a change
     * made by the user mid-call is not applied to the current evaluation. The next incoming call
     * will pick up the new value.
     *
     * @param rawPhoneNumber The raw phone number string from the call details (e.g. "+15551234567",
     *   "5551234567", or empty/unknown). Normalisation is attempted internally.
     * @return A [CallDecision] describing the action
     *   [com.callbloqued.sift.data.service.IncomingCallScreeningService] should instruct the
     *   Android telecom stack to perform.
     */
    suspend operator fun invoke(rawPhoneNumber: String): CallDecision {
        if (!settingsRepository.isFilterEnabled().first()) return CallDecision.Allow

        val normalizedNumber = normalizer.normalize(rawPhoneNumber)
            ?: return CallDecision.Allow

        if (repositories.contactsRepository.isKnownContact(normalizedNumber)) {
            return CallDecision.Allow
        }

        val manualDecision = checkManualLists(normalizedNumber)
        if (manualDecision != null) return manualDecision

        val requiredAttempts = settingsRepository.getRequiredAttemptCount().first()
        val previousAttempts = repositories.callAttemptRepository.getAttemptCount(normalizedNumber)

        return if (previousAttempts >= requiredAttempts) {
            CallDecision.Allow
        } else {
            recordAttemptBlock(normalizedNumber)
            CallDecision.DisallowSilently
        }
    }

    /**
     * Checks the manual blacklist and whitelist and returns the corresponding [CallDecision],
     * or null if the number is in neither list and the attempt-threshold logic should proceed.
     *
     * Blacklist is checked before whitelist. This function is only reached after the
     * device-contacts check (rule 3 in the class-level decision flow) has already passed,
     * so a contact that also appears in the blacklist is never seen here.
     *
     * For a blacklisted number the block event is logged in
     * [ScreeningRepositories.callLogRepository] but the attempt counter in
     * [ScreeningRepositories.callAttemptRepository] is not incremented, because the
     * attempt-threshold "allow after N retries" semantic does not apply to explicitly
     * blacklisted numbers.
     *
     * @param normalizedNumber The caller's phone number in E.164 format.
     * @return [CallDecision.DisallowSilently] if blacklisted, [CallDecision.Allow] if
     *   whitelisted, or null if the number is absent from both lists.
     */
    private suspend fun checkManualLists(normalizedNumber: String): CallDecision? {
        if (repositories.manualListRepository.isBlacklisted(normalizedNumber)) {
            recordBlacklistBlock(normalizedNumber)
            return CallDecision.DisallowSilently
        }
        if (repositories.manualListRepository.isWhitelisted(normalizedNumber)) {
            return CallDecision.Allow
        }
        return null
    }

    /**
     * Records one blocked attempt in the aggregate store and logs the individual block event
     * with reason [BlockReason.ATTEMPT_THRESHOLD].
     *
     * Both writes are sequential; a failure in [ScreeningRepositories.callLogRepository] does
     * not roll back the attempt record.
     *
     * @param normalizedNumber The caller's phone number in E.164 format.
     */
    private suspend fun recordAttemptBlock(normalizedNumber: String) {
        repositories.callAttemptRepository.recordAttempt(normalizedNumber)
        repositories.callLogRepository.logBlockedCall(normalizedNumber, BlockReason.ATTEMPT_THRESHOLD)
    }

    /**
     * Logs a block event for a manually blacklisted number with reason [BlockReason.MANUAL_BLACKLIST].
     *
     * The attempt counter is intentionally not incremented: the "allow after N retries" semantic
     * does not apply to numbers the user has explicitly blacklisted.
     *
     * @param normalizedNumber The caller's phone number in E.164 format.
     */
    private suspend fun recordBlacklistBlock(normalizedNumber: String) {
        repositories.callLogRepository.logBlockedCall(normalizedNumber, BlockReason.MANUAL_BLACKLIST)
    }
}
