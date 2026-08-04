package com.callbloqued.sift.data.repository

import android.provider.ContactsContract
import android.util.Log
import com.callbloqued.sift.data.local.contacts.ContactsContentResolverGateway
import com.callbloqued.sift.domain.repository.ContactsRepository
import javax.inject.Inject

/**
 * [ContactsContentResolverGateway]-backed implementation of [ContactsRepository].
 *
 * The [phoneNumber] parameter is expected to arrive already normalised in E.164 format by
 * [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase] before this method is
 * called. E.164 is universally parseable by [ContactsContract.PhoneLookup], so no additional
 * normalisation is needed here.
 *
 * The [android.Manifest.permission.READ_CONTACTS] permission is normally granted during
 * onboarding (F5), but [isKnownContact] tolerates it being missing — see its KDoc for the
 * fail-safe behaviour that guarantees rule 1 (never block a known contact) still holds.
 *
 * Bound to [ContactsRepository] via `@Binds` in
 * [com.callbloqued.sift.core.di.RepositoryModule].
 *
 * @param gateway Wraps the raw [android.content.ContentResolver] query.
 */
class ContactsRepositoryImpl @Inject constructor(
    private val gateway: ContactsContentResolverGateway
) : ContactsRepository {

    /**
     * Checks whether [phoneNumber] matches any contact stored on the device.
     *
     * Returns `false` (non-contact) when [ContactsContentResolverGateway.countMatchingContacts]
     * finds no match.
     *
     * Returns `true` (treat as a known contact) when [android.Manifest.permission.READ_CONTACTS]
     * is not granted, which makes the underlying [android.content.ContentResolver.query] throw
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
    override suspend fun isKnownContact(phoneNumber: String): Boolean =
        try {
            gateway.countMatchingContacts(phoneNumber) > 0
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS not granted; treating caller as a known contact to fail safe", e)
            true
        }

    private companion object {
        private const val TAG = "ContactsRepositoryImpl"
    }
}
