package com.callbloqued.sift.domain.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts raw phone number strings to the canonical E.164 format using libphonenumber.
 *
 * Centralizing normalization here ensures that numbers arriving in different representations
 * (+1-555-123-4567, 15551234567, 005551234567, etc.) are consistently compared against the
 * same stored form, eliminating false negatives in contact and history lookups.
 *
 * This class lives in `domain` because normalisation is a pure business rule: it depends only
 * on the libphonenumber Java library (no Android SDK imports) and is consumed by both the
 * domain use-case layer and the data layer.
 *
 * A singleton instance is provided by Hilt and reused across all injection points to avoid
 * repeated initialisation of [PhoneNumberUtil], which is itself a heavyweight singleton.
 */
@Singleton
class PhoneNumberNormalizer @Inject constructor() {

    private val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Attempts to parse [rawNumber] and return its E.164 representation.
     *
     * When [rawNumber] does not include a country-code prefix, [defaultRegion] is used as the
     * assumed origin country (ISO 3166-1 alpha-2, e.g. "US", "PE"). Numbers that already carry
     * an explicit country code (starting with "+") are parsed successfully regardless of the
     * default region.
     *
     * Returns `null` if the number cannot be parsed or is structurally invalid according to
     * libphonenumber. Callers must handle the null case explicitly — typically by failing open
     * (allowing the call) to avoid incorrectly blocking a number that merely has an unusual
     * format (see [com.callbloqued.sift.domain.usecase.EvaluateIncomingCallUseCase]).
     *
     * @param rawNumber The phone number in any format (e.g. "+15551234567", "555-1234").
     * @param defaultRegion ISO 3166-1 alpha-2 country code used when [rawNumber] lacks an
     *   explicit country prefix. Defaults to the device's current locale country. Falls back to
     *   "US" when the locale country code is blank.
     * @return The number in E.164 format (e.g. "+15551234567"), or `null` on parse failure.
     */
    fun normalize(
        rawNumber: String,
        defaultRegion: String = Locale.getDefault().country.ifEmpty { "US" }
    ): String? {
        return try {
            val parsed = phoneNumberUtil.parse(rawNumber, defaultRegion)
            if (phoneNumberUtil.isValidNumber(parsed)) {
                phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (e: NumberParseException) {
            null
        }
    }
}
