package io.github.charlescrtech.invoicenow.suppliers.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Explicitly synthetic registration-shaped identifier; never a real UEN. */
public record RegistrationIdentifier(String value) {

    private static final Pattern FORMAT = Pattern.compile("SYNTH-[A-Z0-9][A-Z0-9-]{2,57}");

    public RegistrationIdentifier {
        Objects.requireNonNull(value, "registration identifier must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "registration identifier must start with SYNTH- and contain 9 to 64 supported characters");
        }
    }

    public static RegistrationIdentifier of(String value) {
        return new RegistrationIdentifier(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
