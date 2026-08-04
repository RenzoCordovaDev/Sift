package com.callbloqued.sift.domain.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Unit tests for [PhoneNumberNormalizer].
 *
 * Covers the main branches of [PhoneNumberNormalizer.normalize]:
 * - A number already in E.164 format is returned unchanged.
 * - A local number without a country prefix is resolved using the explicit [defaultRegion]
 *   parameter so results are locale-independent on every CI machine.
 * - Numbers that cannot be parsed or are structurally invalid return `null`.
 * - Empty and blank strings return `null`.
 *
 * Test phone numbers are obtained from [PhoneNumberUtil.getExampleNumber] so they are
 * guaranteed to be structurally valid according to libphonenumber, regardless of the version
 * of the library in use. No hardcoded real subscriber numbers are committed to the repo.
 *
 * No Android runtime is required; the class depends only on libphonenumber (pure JVM).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhoneNumberNormalizerTest {

    private val normalizer = PhoneNumberNormalizer()
    private val phoneUtil = PhoneNumberUtil.getInstance()

    private lateinit var usE164: String
    private lateinit var usNational: String
    private lateinit var peE164: String

    @BeforeAll
    fun buildExampleNumbers() {
        val usProto = phoneUtil.getExampleNumber("US")
        usE164 = phoneUtil.format(usProto, PhoneNumberUtil.PhoneNumberFormat.E164)
        usNational = phoneUtil.format(usProto, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            .replace(Regex("[^0-9]"), "")

        val peProto = phoneUtil.getExampleNumber("PE")
        peE164 = phoneUtil.format(peProto, PhoneNumberUtil.PhoneNumberFormat.E164)
    }

    @Test
    fun `normalize should return E164 when number is already in E164 format`() {
        val result = normalizer.normalize(usE164, defaultRegion = "US")
        assertEquals(usE164, result)
    }

    @Test
    fun `normalize should return E164 when number lacks country prefix and defaultRegion is US`() {
        val result = normalizer.normalize(usNational, defaultRegion = "US")
        assertEquals(usE164, result)
    }

    @Test
    fun `normalize should return non-null E164 for example PE number`() {
        val result = normalizer.normalize(peE164, defaultRegion = "PE")
        assertNotNull(result)
        assertEquals(peE164, result)
    }

    @Test
    fun `normalize should return null when number is empty string`() {
        val result = normalizer.normalize("", defaultRegion = "US")
        assertNull(result)
    }

    @Test
    fun `normalize should return null when number is not parseable`() {
        val result = normalizer.normalize("not-a-number", defaultRegion = "US")
        assertNull(result)
    }

    @Test
    fun `normalize should return null when number is too short to be valid for region`() {
        val result = normalizer.normalize("123", defaultRegion = "US")
        assertNull(result)
    }

    @Test
    fun `normalize should return null when number has only plus sign`() {
        val result = normalizer.normalize("+", defaultRegion = "US")
        assertNull(result)
    }

    @Test
    fun `normalize should return null when number contains only letters`() {
        val result = normalizer.normalize("PHONENUM", defaultRegion = "US")
        assertNull(result)
    }
}
