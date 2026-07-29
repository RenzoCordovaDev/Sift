package com.callbloqued.sift.domain.repository

/**
 * Contract for querying the device's address book.
 *
 * Implementations live in the `data` layer and interact with [android.provider.ContactsContract].
 * This interface deliberately carries no Android imports so that the `domain` layer stays
 * framework-agnostic and can be unit-tested without an Android runtime.
 *
 * Full implementation is delivered in F1 (call-screening core).
 */
interface ContactsRepository {

    /**
     * Checks whether [phoneNumber] belongs to any contact stored on the device.
     *
     * The number is expected to be in E.164 format (normalised by the caller before invoking
     * this method).  Normalisation logic using libphonenumber is introduced in F1.
     *
     * @param phoneNumber Phone number in E.164 format (e.g. "+15551234567").
     * @return `true` if at least one device contact matches the given number; `false` otherwise.
     */
    suspend fun isKnownContact(phoneNumber: String): Boolean
}
