package io.github.charlescrtech.invoicenow.suppliers.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable normalized business key for a synthetic supplier. */
public record SupplierCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,31}");

    public SupplierCode {
        Objects.requireNonNull(value, "supplier code must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "supplier code must contain 3 to 32 uppercase letters, digits, underscores, or hyphens");
        }
    }

    public static SupplierCode of(String value) {
        return new SupplierCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
