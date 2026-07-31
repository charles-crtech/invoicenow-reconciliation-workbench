package io.github.charlescrtech.invoicenow.reconciliation.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record AccountCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._-]{2,31}");

    public AccountCode {
        Objects.requireNonNull(value, "account code must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("account code must contain 3 to 32 supported characters");
        }
    }

    public static AccountCode of(String value) {
        return new AccountCode(value);
    }
}
