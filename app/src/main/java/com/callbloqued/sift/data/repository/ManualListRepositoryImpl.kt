package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.db.ManualListDao
import com.callbloqued.sift.data.local.db.ManualListEntity
import com.callbloqued.sift.domain.model.ManualListEntry
import com.callbloqued.sift.domain.model.ManualListType
import com.callbloqued.sift.domain.repository.ManualListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [ManualListRepository].
 *
 * Maps between [ManualListEntity] (persistence layer) and [ManualListEntry] (domain model).
 * The [ManualListType] enum is stored by name in the database so that new values added in
 * future phases do not require a schema migration.
 *
 * Unknown [ManualListEntity.listType] strings found in existing rows (e.g. written by a newer
 * app version and read by an older one) are silently excluded from [observeList] results and
 * treated as absent in point-lookup queries, preventing crashes on forward-compatibility
 * scenarios.
 *
 * Bound to [ManualListRepository] via `@Binds` in
 * [com.callbloqued.sift.core.di.RepositoryModule].
 *
 * @param manualListDao Room DAO used to read and write [ManualListEntity] records.
 */
class ManualListRepositoryImpl @Inject constructor(
    private val manualListDao: ManualListDao
) : ManualListRepository {

    /**
     * Returns true if [normalizedPhoneNumber] has an entry with type [ManualListType.BLACKLIST].
     *
     * @param normalizedPhoneNumber The caller's phone number in E.164 format.
     * @return `true` if the number is found in the blacklist; `false` otherwise.
     */
    override suspend fun isBlacklisted(normalizedPhoneNumber: String): Boolean =
        manualListDao.findByNumberAndType(
            normalizedPhoneNumber,
            ManualListType.BLACKLIST.name
        ) != null

    /**
     * Returns true if [normalizedPhoneNumber] has an entry with type [ManualListType.WHITELIST].
     *
     * @param normalizedPhoneNumber The caller's phone number in E.164 format.
     * @return `true` if the number is found in the whitelist; `false` otherwise.
     */
    override suspend fun isWhitelisted(normalizedPhoneNumber: String): Boolean =
        manualListDao.findByNumberAndType(
            normalizedPhoneNumber,
            ManualListType.WHITELIST.name
        ) != null

    /**
     * Inserts or replaces the entry for [phoneNumber] with [listType] as the current timestamp.
     *
     * @param phoneNumber The phone number in E.164 format to add.
     * @param listType The list to place the number in.
     */
    override suspend fun addToList(phoneNumber: String, listType: ManualListType) {
        manualListDao.upsert(
            ManualListEntity(
                phoneNumber = phoneNumber,
                listType = listType.name,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Deletes the entry for [phoneNumber] from any list it currently belongs to.
     *
     * @param phoneNumber The phone number in E.164 format to remove.
     */
    override suspend fun removeFromList(phoneNumber: String) {
        manualListDao.delete(phoneNumber)
    }

    /**
     * Returns a [Flow] emitting all entries for [listType] mapped to [ManualListEntry],
     * ordered by most-recently-added first. Entries with unrecognised type strings are excluded.
     *
     * @param listType The list to observe.
     * @return A [Flow] of [ManualListEntry] lists for [listType], newest first.
     */
    override fun observeList(listType: ManualListType): Flow<List<ManualListEntry>> =
        manualListDao.observeByType(listType.name).map { entities ->
            entities.mapNotNull { it.toDomainModel() }
        }

    /**
     * Maps a [ManualListEntity] to the domain model [ManualListEntry].
     *
     * Returns null if [ManualListEntity.listType] cannot be parsed as a known [ManualListType],
     * protecting against forward-compatibility issues when an older app version reads rows
     * written by a newer version that introduced additional list types.
     *
     * @return The corresponding [ManualListEntry], or null if the type string is unrecognised.
     */
    private fun ManualListEntity.toDomainModel(): ManualListEntry? {
        val type = runCatching { ManualListType.valueOf(listType) }.getOrNull() ?: return null
        return ManualListEntry(
            phoneNumber = phoneNumber,
            listType = type,
            addedAt = addedAt
        )
    }
}
