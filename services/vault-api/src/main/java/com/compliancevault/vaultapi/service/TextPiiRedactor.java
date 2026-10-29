package com.compliancevault.vaultapi.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextPiiRedactor {

    private static final Pattern AADHAAR =
            Pattern.compile("(?<!\\d)\\d{4}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)");

    private static final Pattern PAN =
            Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");

    private static final Pattern EMAIL =
            Pattern.compile(
                    "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");

    private static final Pattern INDIAN_PHONE =
            Pattern.compile("(?<!\\d)(?:\\+?91[ -]?)?[6-9]\\d{9}(?!\\d)");

    public RedactionResult redact(String input) {
        Replacement aadhaar = replace(AADHAAR, input, "[REDACTED_AADHAAR]");
        Replacement pan = replace(PAN, aadhaar.text(), "[REDACTED_PAN]");
        Replacement email = replace(EMAIL, pan.text(), "[REDACTED_EMAIL]");
        Replacement phone =
                replace(INDIAN_PHONE, email.text(), "[REDACTED_PHONE]");

        return new RedactionResult(
                phone.text(),
                aadhaar.count(),
                pan.count(),
                email.count(),
                phone.count());
    }

    private Replacement replace(Pattern pattern, String input, String replacement) {
        Matcher matcher = pattern.matcher(input);
        int count = 0;

        while (matcher.find()) {
            count++;
        }

        return new Replacement(
                pattern.matcher(input).replaceAll(replacement),
                count);
    }

    private record Replacement(String text, int count) {}

    public record RedactionResult(
            String text,
            int aadhaarCount,
            int panCount,
            int emailCount,
            int phoneCount) {}
}