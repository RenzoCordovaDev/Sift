package com.callbloqued.sift.domain.model

/**
 * Represents a single entry in the user-managed blacklist or whitelist.
 *
 * A phone number can appear at most once across all manual lists; placing it in one list
 * implicitly removes it from the other (enforced at the persistence level via the primary key
 * on [com.callbloqued.sift.data.local.db.ManualListEntity]).
 *
 * Numbers are stored in E.164 format after normalisation by
 * [com.callbloqued.sift.domain.util.PhoneNumberNormalizer].
 *
 * Instances are produced by [com.callbloqued.sift.domain.repository.ManualListRepository]
 * and consumed by the UI layer (F4) without any Android or Room dependencies.
 *
 * @property phoneNumber The phone number in E.164 format (e.g. "+15551234567").
 * @property listType Whether this entry is in the [ManualListType.BLACKLIST] or [ManualListType.WHITELIST].
 * @property addedAt Unix epoch milliseconds when the entry was created by the user.
 */
data class ManualListEntry(
    val phoneNumber: String,
    val listType: ManualListType,
    val addedAt: Long
)
