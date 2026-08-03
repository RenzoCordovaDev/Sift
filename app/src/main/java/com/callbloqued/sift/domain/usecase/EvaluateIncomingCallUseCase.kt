package com.callbloqued.sift.domain.usecase

import com.callbloqued.sift.domain.model.CallDecision
import com.callbloqued.sift.domain.repository.CallAttemptRepository
import com.callbloqued.sift.domain.repository.ContactsRepository
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
 *    (non-negotiable rule: known contacts are never blocked).
 * 4. If the number has already been blocked ≥ [SettingsRepository.getRequiredAttemptCount]
 *    times → [CallDecision.Allow] (caller has persisted; treat as legitimate).
 * 5. Otherwise → record the attempt and return [CallDecision.DisallowSilently].
 *
 * **Out of scope for F1:** manual blacklist/whitelist checks (added in F3).
 *
 * @param contactsRepository Used to check whether the caller is a known device contact.
 * @param callAttemptRepository Used to read and write blocked-attempt history.
 * @param settingsRepository Used to read the filter-enabled flag and the attempt threshold.
 * @param normalizer Used to convert the raw caller number to a canonical E.164 string.
 */
class EvaluateIncomingCallUseCase @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val callAttemptRepository: CallAttemptRepository,
    private val settingsRepository: SettingsRepository,
    private val normalizer: PhoneNumberNormalizer
) {

    /**
     * Evaluates the incoming call identified by [rawPhoneNumber] and returns the action to take.
     *
     * This is a suspending operator function so callers can invoke it as
     * `evaluateIncomingCallUseCase(number)` from within a coroutine.
     *
     * Settings values ([SettingsRepository.isFilterEnabled], [SettingsRepository.getRequiredAttemptCount])
     * are read as one-shot snapshots via [Flow.first] so that a change made by the user mid-call
     * is not applied to the current evaluation. The next incoming call will pick up the new value.
     *
     * @param rawPhoneNumber The raw phone number string from the call details (e.g. "+15551234567",
     *   "5551234567", or empty/unknown). Normalisation is attempted internally.
     * @return A [CallDecision] describing the action [com.callbloqued.sift.data.service.IncomingCallScreeningService]
     *   should instruct the Android telecom stack to perform.
     */
    suspend operator fun invoke(rawPhoneNumber: String): CallDecision {
        if (!settingsRepository.isFilterEnabled().first()) {
            return CallDecision.Allow
        }

        val normalizedNumber = normalizer.normalize(rawPhoneNumber)
            ?: return CallDecision.Allow

        if (contactsRepository.isKnownContact(normalizedNumber)) {
            return CallDecision.Allow
        }

        val requiredAttempts = settingsRepository.getRequiredAttemptCount().first()
        val previousAttempts = callAttemptRepository.getAttemptCount(normalizedNumber)

        return if (previousAttempts >= requiredAttempts) {
            CallDecision.Allow
        } else {
            callAttemptRepository.recordAttempt(normalizedNumber)
            CallDecision.DisallowSilently
        }
    }
}
