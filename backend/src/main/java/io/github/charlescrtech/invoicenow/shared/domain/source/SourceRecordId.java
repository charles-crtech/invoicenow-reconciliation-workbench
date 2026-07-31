package io.github.charlescrtech.invoicenow.shared.domain.source;

import java.util.Objects;
import java.util.regex.Pattern;

public record SourceRecordId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}");

    public SourceRecordId {
        Objects.requireNonNull(value, "source record ID must not be null");
        value = value.strip();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("source record ID must contain 1 to 100 supported characters");
        }
    }

    public static SourceRecordId of(String value) {
        return new SourceRecordId(value);
    }
}
