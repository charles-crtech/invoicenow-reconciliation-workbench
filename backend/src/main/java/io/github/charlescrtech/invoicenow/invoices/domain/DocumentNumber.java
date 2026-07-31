package io.github.charlescrtech.invoicenow.invoices.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record DocumentNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,63}");

    public DocumentNumber {
        Objects.requireNonNull(value, "document number must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("document number must contain 1 to 64 supported characters");
        }
    }

    public static DocumentNumber of(String value) {
        return new DocumentNumber(value);
    }
}
