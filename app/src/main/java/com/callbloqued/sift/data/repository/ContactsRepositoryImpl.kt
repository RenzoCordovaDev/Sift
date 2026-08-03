package com.callbloqued.sift.data.repository

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.callbloqued.sift.domain.repository.ContactsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * ContentResolver-backed implementation of [ContactsRepository].
 *
 * Queries [ContactsContract.PhoneLookup] to determine whether a given phone number belongs to
 * any contact stored on the device. [PhoneLookup] is Android's dedicated reverse-lookup URI: it
 * accepts a raw phone number string, normalises it internally using the device's locale and
 * telephony settings, and returns the matching contacts. This is preferred over iterating all
 * contacts manually because:
 *   1. Android handles format variations (+1-555-123-4567 vs 5551234567) natively.
 *   2. A single indexed cursor lookup is significantly faster than a full table scan.
 *
 * The [phoneNumber] parameter is expected to arrive already normalised in E.164 format by
 * [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase] before this method is
 * called. E.164 is universally parseable by [PhoneLookup], so no additional normalisation is
 * needed here.
 *
 * The [android.Manifest.permission.READ_CONTACTS] permission is normally granted during
 * onboarding (F5), but [isKnownContact] tolerates it being missing — see its KDoc for the
 * fail-safe behaviour that guarantees rule 1 (never block a known contact) still holds.
 *
 * Bound to [ContactsRepository] via `@Binds` in
 * [com.callbloqued.sift.core.di.RepositoryModule].
 *
 * @param context Application context used to obtain a [android.content.ContentResolver].
 */
class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactsRepository {

    /**
     * Checks whether [phoneNumber] matches any contact stored on the device.
     *
     * Uses [ContactsContract.PhoneLookup.CONTENT_FILTER_URI] which accepts the phone number
     * directly in the URI path. The cursor row count indicates whether a match was found; the
     * actual contact data is not read, keeping the query as lightweight as possible.
     *
     * Returns `false` (non-contact) when the cursor itself is null, which can happen on some
     * OEM ContentProvider implementations even with the permission granted.
     *
     * Returns `true` (treat as a known contact) when [android.Manifest.permission.READ_CONTACTS]
     * is not granted, which makes [android.content.ContentResolver.query] throw
     * [SecurityException]. This is the only way to honour CLAUDE.md's non-negotiable rule 1
     * ("never block a number that is in device contacts") in a state where we are structurally
     * unable to verify contact status: returning `false` here would let the decision flow
     * proceed to attempt-tracking and risk silently blocking a real contact. The practical
     * effect is that screening is inert until the permission is granted (F5 onboarding), which
     * is the safe default.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+15551234567").
     * @return `true` if at least one device contact matches the number, or if contact status
     *   cannot be verified; `false` if the lookup ran but found no match.
     */
    override suspend fun isKnownContact(phoneNumber: String): Boolean {
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        return try {
            val cursor = context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )
            cursor?.use { it.count > 0 } ?: false
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS not granted; treating caller as a known contact to fail safe", e)
            true
        }
    }

    private companion object {
        private const val TAG = "ContactsRepositoryImpl"
    }
}
