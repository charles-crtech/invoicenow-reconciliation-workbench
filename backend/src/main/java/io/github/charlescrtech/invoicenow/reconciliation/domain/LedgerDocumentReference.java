package io.github.charlescrtech.invoicenow.reconciliation.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record LedgerDocumentReference(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,63}");

    public LedgerDocumentReference {
        Objects.requireNonNull(value, "ledger document reference must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "ledger document reference must contain 1 to 64 supported characters");
        }
    }

    public static LedgerDocumentReference of(String value) {
        return new LedgerDocumentReference(value);
    }
}
