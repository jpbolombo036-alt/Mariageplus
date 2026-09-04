package com.mariageplus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneNormalizerTest {

    @Test
    void nullOrBlank_returnsNull() {
        assertNull(PhoneNormalizer.toE164(null, "225"));
        assertNull(PhoneNormalizer.toE164("  ", "225"));
    }

    @Test
    void alreadyE164_keptAsIs() {
        assertEquals("+2250701020304", PhoneNormalizer.toE164("+2250701020304", "225"));
    }

    @Test
    void spacesAndDashes_removed() {
        assertEquals("+2250701020304", PhoneNormalizer.toE164("+225 07 01-02-03-04", "225"));
    }

    @Test
    void doubleZeroPrefix_converted() {
        assertEquals("+2250701020304", PhoneNormalizer.toE164("00225 07 01 02 03 04", "225"));
    }

    @Test
    void localNumber_prefixedWithDefaultCountryCode() {
        // 0701020304 (local CI) → +225 701020304 (le 0 local est retiré)
        assertEquals("+225701020304", PhoneNormalizer.toE164("07 01 02 03 04", "225"));
    }

    @Test
    void localNumberWithoutCountryCode_returnsNull() {
        assertNull(PhoneNormalizer.toE164("0701020304", ""));
        assertNull(PhoneNormalizer.toE164("0701020304", null));
    }

    @Test
    void tooShortOrTooLong_returnsNull() {
        assertNull(PhoneNormalizer.toE164("+2250701", "225"));
        assertNull(PhoneNormalizer.toE164("+1234567890123456789", "225"));
    }

    @Test
    void lettersOnly_returnsNull() {
        assertNull(PhoneNormalizer.toE164("abc", "225"));
    }

    @Test
    void toWhatsAppId_stripsPlus() {
        assertEquals("2250701020304", PhoneNormalizer.toWhatsAppId("+2250701020304"));
        assertNull(PhoneNormalizer.toWhatsAppId(null));
        assertNull(PhoneNormalizer.toWhatsAppId("not-a-number"));
    }
}
