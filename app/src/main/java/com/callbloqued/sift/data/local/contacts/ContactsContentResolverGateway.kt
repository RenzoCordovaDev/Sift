package com.callbloqued.sift.data.local.contacts

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Thin wrapper around the device's [android.content.ContentResolver] for contact lookups.
 *
 * Isolates the raw [ContactsContract.PhoneLookup] query from
 * [com.callbloqued.sift.data.repository.ContactsRepositoryImpl] so the fail-safe decision logic
 * around it (see that class's KDoc) can be unit-tested with a plain mock of this gateway,
 * instead of needing to fake Android's `ContactsContract` static fields — which are `null` in
 * the JVM unit-test stub jar and can only be exercised on a real device or emulator.
 *
 * Like [com.callbloqued.sift.data.local.db.AppDatabase], this class is declarative Android
 * framework glue with no business logic of its own, so it's excluded from the JaCoCo coverage
 * gate (see `app/build.gradle.kts`) and is instead verified by an instrumented test.
 */
class ContactsContentResolverGateway @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Counts how many device contacts have a phone number matching [phoneNumber].
     *
     * Uses [ContactsContract.PhoneLookup.CONTENT_FILTER_URI], Android's dedicated reverse-lookup
     * URI: it accepts a raw phone number string, normalises it internally using the device's
     * locale and telephony settings, and returns the matching contacts. This is preferred over
     * iterating all contacts manually because:
     *   1. Android handles format variations (+1-555-123-4567 vs 5551234567) natively.
     *   2. A single indexed cursor lookup is significantly faster than a full table scan.
     *
     * The actual contact data is not read — only the row count — keeping the query as
     * lightweight as possible.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+15551234567").
     * @return The number of matching contacts, or 0 if the cursor is null (can happen on some
     *   OEM ContentProvider implementations even with the permission granted).
     * @throws SecurityException if [android.Manifest.permission.READ_CONTACTS] is not granted.
     */
    fun countMatchingContacts(phoneNumber: String): Int {
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

        return cursor?.use { it.count } ?: 0
    }
}
