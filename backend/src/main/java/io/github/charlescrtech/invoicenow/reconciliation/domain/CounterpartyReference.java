package io.github.charlescrtech.invoicenow.reconciliation.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CounterpartyReference(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,63}");

    public CounterpartyReference {
        Objects.requireNonNull(value, "counterparty reference must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("counterparty reference must contain 1 to 64 supported characters");
        }
    }

    public static CounterpartyReference of(String value) {
        return new CounterpartyReference(value);
    }
}
