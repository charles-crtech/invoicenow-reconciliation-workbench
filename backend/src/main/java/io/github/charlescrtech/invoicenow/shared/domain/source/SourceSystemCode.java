package io.github.charlescrtech.invoicenow.shared.domain.source;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record SourceSystemCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{2,31}");

    public SourceSystemCode {
        Objects.requireNonNull(value, "source system code must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("source system code must be 3 to 32 uppercase letters, digits, or underscores");
        }
    }

    public static SourceSystemCode of(String value) {
        return new SourceSystemCode(value);
    }
}
