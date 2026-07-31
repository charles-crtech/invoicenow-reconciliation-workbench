package io.github.charlescrtech.invoicenow.shared.domain.identifier;

import java.util.Objects;
import java.util.UUID;

/** Shared parsing guard for module-owned UUID identifier types. */
public final class UuidIdentifiers {

    private UuidIdentifiers() {
    }

    public static UUID parseCanonical(String rawValue, String label) {
        Objects.requireNonNull(rawValue, label + " must not be null");
        if (rawValue.isBlank() || !rawValue.equals(rawValue.trim())) {
            throw new IllegalArgumentException(label + " must be a canonical UUID");
        }

        try {
            UUID parsed = UUID.fromString(rawValue);
            if (!parsed.toString().equalsIgnoreCase(rawValue)) {
                throw new IllegalArgumentException(label + " must be a canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " must be a canonical UUID", exception);
        }
    }
}
