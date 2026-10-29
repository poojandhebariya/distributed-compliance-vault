package com.compliancevault.vaultapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextPiiRedactorTest {

    private final TextPiiRedactor redactor = new TextPiiRedactor();

    @Test
    void redactsAllSupportedPiiAndCountsMatches() {
        String input = "Aadhaar 1234 5678 9012, PAN ABCDE1234F, "
                + "email test@example.com, phone 9876543210.";

        TextPiiRedactor.RedactionResult result = redactor.redact(input);

        assertEquals(
                "Aadhaar [REDACTED_AADHAAR], PAN [REDACTED_PAN], "
                        + "email [REDACTED_EMAIL], phone [REDACTED_PHONE].",
                result.text());

        assertEquals(1, result.aadhaarCount());
        assertEquals(1, result.panCount());
        assertEquals(1, result.emailCount());
        assertEquals(1, result.phoneCount());
    }

    @Test
    void redactsMultipleMatchesOfEachType() {
        String input = "123456789012 234567890123 "
                + "ABCDE1234F FGHIJ5678K "
                + "first@example.com second@example.org "
                + "9876543210 8765432109";

        TextPiiRedactor.RedactionResult result = redactor.redact(input);

        assertEquals(2, result.aadhaarCount());
        assertEquals(2, result.panCount());
        assertEquals(2, result.emailCount());
        assertEquals(2, result.phoneCount());
    }

    @Test
    void preservesOrdinaryTextWhenNoPiiIsPresent() {
        String input = "Compliance Vault processing completed successfully.";

        TextPiiRedactor.RedactionResult result = redactor.redact(input);

        assertEquals(input, result.text());
        assertEquals(0, result.aadhaarCount());
        assertEquals(0, result.panCount());
        assertEquals(0, result.emailCount());
        assertEquals(0, result.phoneCount());
    }

    @Test
    void handlesEmptyInput() {
        TextPiiRedactor.RedactionResult result = redactor.redact("");

        assertEquals("", result.text());
        assertEquals(0, result.aadhaarCount());
        assertEquals(0, result.panCount());
        assertEquals(0, result.emailCount());
        assertEquals(0, result.phoneCount());
    }
}
