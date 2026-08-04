package com.callbloqued.sift.domain.usecase

import com.callbloqued.sift.domain.repository.CallAttemptRepository
import com.callbloqued.sift.domain.repository.CallLogRepository
import com.callbloqued.sift.domain.repository.ContactsRepository
import com.callbloqued.sift.domain.repository.ManualListRepository
import javax.inject.Inject

/**
 * Groups the four data-access repositories required by [EvaluateIncomingCallUseCase].
 *
 * This wrapper exists solely to keep [EvaluateIncomingCallUseCase]'s constructor within
 * the five-parameter limit defined in CODE_QUALITY_STANDARDS.md §4. Without grouping, the
 * use case would require six constructor parameters (the four below plus [SettingsRepository]
 * and [com.callbloqued.sift.domain.util.PhoneNumberNormalizer]).
 *
 * All four repositories are injected as their interface types so the `domain` layer remains
 * decoupled from `data`-layer implementations.
 *
 * @property contactsRepository Used to check whether the caller is a known device contact.
 * @property callAttemptRepository Used to read and write the blocked-attempt aggregate history.
 * @property callLogRepository Used to persist individual blocked-call events for user history.
 * @property manualListRepository Used to check and manage the user's manual blacklist and whitelist.
 */
data class ScreeningRepositories @Inject constructor(
    val contactsRepository: ContactsRepository,
    val callAttemptRepository: CallAttemptRepository,
    val callLogRepository: CallLogRepository,
    val manualListRepository: ManualListRepository
)
