package io.github.charlescrtech.invoicenow.shared.domain.identifier;

import java.util.UUID;

/** Marker contract for strongly typed domain identifiers backed by UUIDs. */
public interface UuidIdentifier {

    UUID value();

    default String canonicalValue() {
        return value().toString();
    }
}
