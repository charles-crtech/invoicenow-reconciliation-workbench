package io.github.charlescrtech.invoicenow.imports.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

    private static final Pattern SUPPORTED = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    public IdempotencyKey {
        Objects.requireNonNull(value, "idempotency key must not be null");
        if (!SUPPORTED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "idempotency key must contain 8 to 128 supported ASCII characters");
        }
    }

    @Override
    public String toString() {
        return "[redacted-idempotency-key]";
    }
}
