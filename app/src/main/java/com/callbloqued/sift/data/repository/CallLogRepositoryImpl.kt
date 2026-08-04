package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.db.BlockedCallLogDao
import com.callbloqued.sift.data.local.db.BlockedCallLogEntity
import com.callbloqued.sift.domain.model.BlockReason
import com.callbloqued.sift.domain.model.BlockedCallLogEntry
import com.callbloqued.sift.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [CallLogRepository].
 *
 * Persists each blocked-call event as a [BlockedCallLogEntity] row and maps between the
 * Room entity and the domain model [BlockedCallLogEntry]. The [BlockReason] enum is stored
 * by name in the database so that new values added in later phases (e.g. F3 manual blacklist)
 * do not require a schema migration.
 *
 * Unknown reason strings found in the database (written by a newer app version and read by
 * an older one) are mapped to [BlockReason.ATTEMPT_THRESHOLD] as a safe default, preventing
 * crashes on forward-compatibility scenarios.
 *
 * Bound to [CallLogRepository] via `@Binds` in
 * [com.callbloqued.sift.core.di.RepositoryModule].
 *
 * @param blockedCallLogDao Room DAO used to insert and observe [BlockedCallLogEntity] records.
 */
class CallLogRepositoryImpl @Inject constructor(
    private val blockedCallLogDao: BlockedCallLogDao
) : CallLogRepository {

    /**
     * Inserts a new blocked-call event for [phoneNumber] with the current system time.
     *
     * @param phoneNumber The blocked caller's phone number in E.164 format.
     * @param reason The reason the call was blocked (see [BlockReason]).
     */
    override suspend fun logBlockedCall(phoneNumber: String, reason: BlockReason) {
        blockedCallLogDao.insert(
            BlockedCallLogEntity(
                phoneNumber = phoneNumber,
                timestamp = System.currentTimeMillis(),
                reason = reason.name
            )
        )
    }

    /**
     * Returns a [Flow] that emits the full blocked-call log mapped to [BlockedCallLogEntry],
     * ordered by most-recent first.
     *
     * @return A [Flow] of [BlockedCallLogEntry] lists, newest event first.
     */
    override fun observeBlockedCallLog(): Flow<List<BlockedCallLogEntry>> =
        blockedCallLogDao.observeAll().map { entities ->
            entities.map { entity -> entity.toDomainModel() }
        }

    /**
     * Maps a [BlockedCallLogEntity] to the domain model [BlockedCallLogEntry].
     *
     * Unknown [BlockedCallLogEntity.reason] strings are mapped to [BlockReason.ATTEMPT_THRESHOLD]
     * to prevent crashes when a device running an older app version reads records written
     * by a newer version that introduced additional [BlockReason] values.
     *
     * @return The corresponding [BlockedCallLogEntry] with all fields mapped.
     */
    private fun BlockedCallLogEntity.toDomainModel(): BlockedCallLogEntry =
        BlockedCallLogEntry(
            id = id,
            phoneNumber = phoneNumber,
            timestamp = timestamp,
            reason = runCatching { BlockReason.valueOf(reason) }
                .getOrDefault(BlockReason.ATTEMPT_THRESHOLD)
        )
}
