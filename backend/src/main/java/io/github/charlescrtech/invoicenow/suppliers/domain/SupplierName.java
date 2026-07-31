package io.github.charlescrtech.invoicenow.suppliers.domain;

import java.util.Objects;

/** Display label for a supplier; output adapters must still escape it as untrusted text. */
public record SupplierName(String value) {

    public static final int MAX_LENGTH = 200;

    public SupplierName {
        Objects.requireNonNull(value, "supplier name must not be null");
        value = value.strip();
        int codePointLength = value.codePointCount(0, value.length());
        if (value.isBlank() || codePointLength > MAX_LENGTH) {
            throw new IllegalArgumentException("supplier name must contain 1 to 200 characters");
        }
    }

    public static SupplierName of(String value) {
        return new SupplierName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
