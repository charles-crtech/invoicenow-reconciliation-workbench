package io.github.charlescrtech.invoicenow.shared.domain.source;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Sha256Hash(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9a-f]{64}");

    public Sha256Hash {
        Objects.requireNonNull(value, "SHA-256 hash must not be null");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("SHA-256 hash must contain exactly 64 hexadecimal characters");
        }
    }

    public static Sha256Hash of(String value) {
        return new Sha256Hash(value);
    }
}
