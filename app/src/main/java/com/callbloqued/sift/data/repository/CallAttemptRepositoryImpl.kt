package com.callbloqued.sift.data.repository

import com.callbloqued.sift.data.local.db.CallAttemptDao
import com.callbloqued.sift.data.local.db.CallAttemptEntity
import com.callbloqued.sift.domain.repository.CallAttemptRepository
import javax.inject.Inject

/**
 * Room-backed implementation of [CallAttemptRepository].
 *
 * Persists and retrieves silent-block records for each unknown caller. On the first blocked
 * call from a number, a new [CallAttemptEntity] row is inserted with [attemptCount] = 1. On
 * subsequent blocked calls from the same number, the existing row is updated: [lastAttemptAt]
 * is refreshed and [attemptCount] is incremented.
 *
 * Bound to [CallAttemptRepository] via `@Binds` in
 * [com.callbloqued.sift.core.di.RepositoryModule].
 *
 * @param callAttemptDao Room DAO used to read and write [CallAttemptEntity] records.
 */
class CallAttemptRepositoryImpl @Inject constructor(
    private val callAttemptDao: CallAttemptDao
) : CallAttemptRepository {

    /**
     * Returns the total number of times [phoneNumber] has been blocked silently.
     *
     * @param phoneNumber Phone number in E.164 format.
     * @return The [CallAttemptEntity.attemptCount] for this number, or 0 if no record exists.
     */
    override suspend fun getAttemptCount(phoneNumber: String): Int =
        callAttemptDao.findByPhoneNumber(phoneNumber)?.attemptCount ?: 0

    /**
     * Records one blocked attempt for [phoneNumber], creating or updating the history row.
     *
     * If no prior record exists a new [CallAttemptEntity] is inserted with [attemptCount] = 1
     * and both timestamps set to the current time. If a record already exists the existing
     * [firstAttemptAt] is preserved, [lastAttemptAt] is updated, and [attemptCount] is
     * incremented by one.
     *
     * @param phoneNumber Phone number in E.164 format.
     */
    override suspend fun recordAttempt(phoneNumber: String) {
        val now = System.currentTimeMillis()
        val existing = callAttemptDao.findByPhoneNumber(phoneNumber)
        if (existing == null) {
            callAttemptDao.insert(
                CallAttemptEntity(
                    phoneNumber = phoneNumber,
                    firstAttemptAt = now,
                    lastAttemptAt = now,
                    attemptCount = 1
                )
            )
        } else {
            callAttemptDao.update(
                existing.copy(
                    lastAttemptAt = now,
                    attemptCount = existing.attemptCount + 1
                )
            )
        }
    }
}
