package com.callbloqued.sift.domain.repository

import com.callbloqued.sift.domain.model.ManualListEntry
import com.callbloqued.sift.domain.model.ManualListType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and writing the user-managed blacklist and whitelist.
 *
 * Used in two contexts:
 * - **Decision logic:** [isBlacklisted] and [isWhitelisted] are called by
 *   [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase] for every incoming call,
 *   after the device-contacts check and before the attempt-threshold check.
 * - **UI management (F4):** [addToList], [removeFromList], and [observeList] will be consumed
 *   by the manual-lists screen ViewModel to display and edit each list.
 *
 * A phone number is mutually exclusive between lists: adding a number already present in one
 * list to the other replaces the existing entry rather than creating a duplicate. This
 * invariant is enforced at the persistence level via the primary key of
 * [com.callbloqued.sift.data.local.db.ManualListEntity].
 *
 * Implementation ([com.callbloqued.sift.data.repository.ManualListRepositoryImpl]) lives in
 * the `data` layer using Room. This interface has no Android imports, keeping `domain`
 * framework-agnostic.
 */
interface ManualListRepository {

    /**
     * Returns true if [normalizedPhoneNumber] is present in the user's blacklist.
     *
     * Called by the screening decision flow immediately after the device-contacts check.
     * The lookup is performed against the E.164-normalised number to avoid false negatives
     * from formatting differences.
     *
     * @param normalizedPhoneNumber The caller's phone number in E.164 format.
     * @return `true` if the number is blacklisted; `false` otherwise.
     */
    suspend fun isBlacklisted(normalizedPhoneNumber: String): Boolean

    /**
     * Returns true if [normalizedPhoneNumber] is present in the user's whitelist.
     *
     * Called by the screening decision flow after the blacklist check to short-circuit the
     * attempt-threshold logic for numbers the user has explicitly trusted.
     *
     * @param normalizedPhoneNumber The caller's phone number in E.164 format.
     * @return `true` if the number is whitelisted; `false` otherwise.
     */
    suspend fun isWhitelisted(normalizedPhoneNumber: String): Boolean

    /**
     * Adds [phoneNumber] to the specified [listType], replacing any existing entry for that number.
     *
     * If the number already exists in the opposite list it is moved to [listType] rather than
     * creating a duplicate. The number must already be in E.164 format; normalisation is the
     * caller's responsibility.
     *
     * @param phoneNumber The phone number in E.164 format to add.
     * @param listType The list ([ManualListType.BLACKLIST] or [ManualListType.WHITELIST]) to
     *   place the number in.
     */
    suspend fun addToList(phoneNumber: String, listType: ManualListType)

    /**
     * Removes [phoneNumber] from whichever list it currently belongs to.
     *
     * No-ops silently if the number is not present in any list.
     *
     * @param phoneNumber The phone number in E.164 format to remove.
     */
    suspend fun removeFromList(phoneNumber: String)

    /**
     * Returns a [Flow] that emits all entries belonging to [listType], ordered by
     * most-recently-added first.
     *
     * Emits a new list whenever an entry is added to or removed from [listType]. Suitable
     * for direct collection by a ViewModel without manual refresh logic.
     *
     * @param listType The list to observe ([ManualListType.BLACKLIST] or [ManualListType.WHITELIST]).
     * @return A [Flow] of [ManualListEntry] lists for [listType], ordered by
     *   [ManualListEntry.addedAt] descending.
     */
    fun observeList(listType: ManualListType): Flow<List<ManualListEntry>>
}
