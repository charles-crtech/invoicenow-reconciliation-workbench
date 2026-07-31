package io.github.charlescrtech.invoicenow.invoices.domain;

import java.util.Objects;

public record CorrectionReason(String value) {

    public static final int MAX_LENGTH = 500;

    public CorrectionReason {
        Objects.requireNonNull(value, "correction reason must not be null");
        value = value.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 10 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("correction reason must contain 10 to 500 characters");
        }
    }

    public static CorrectionReason of(String value) {
        return new CorrectionReason(value);
    }
}
