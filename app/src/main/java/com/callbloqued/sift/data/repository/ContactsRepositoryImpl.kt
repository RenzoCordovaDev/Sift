package com.callbloqued.sift.data.repository

import android.content.Context
import android.provider.ContactsContract
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
 * Requires the [android.Manifest.permission.READ_CONTACTS] permission to be granted at runtime
 * before this repository is called. The permission is requested during onboarding (F5).
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
     * Returns `false` (non-contact) when the ContentResolver is unavailable or the cursor is
     * null, rather than throwing. A null cursor typically signals a missing READ_CONTACTS
     * permission; in that edge case failing open (treating the number as non-contact) might let
     * spam through, but it avoids blocking a genuine caller due to a missing runtime permission.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+15551234567").
     * @return `true` if at least one device contact matches the number; `false` otherwise.
     */
    override suspend fun isKnownContact(phoneNumber: String): Boolean {
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        val cursor = context.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )

        return cursor?.use { it.count > 0 } ?: false
    }
}
