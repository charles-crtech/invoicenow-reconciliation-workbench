package io.github.charlescrtech.invoicenow.suppliers.domain;

import io.github.charlescrtech.invoicenow.shared.domain.identifier.UuidIdentifier;
import io.github.charlescrtech.invoicenow.shared.domain.identifier.UuidIdentifiers;
import java.util.Objects;
import java.util.UUID;

public record SupplierId(UUID value) implements UuidIdentifier {

    public SupplierId {
        Objects.requireNonNull(value, "supplier ID value must not be null");
    }

    public static SupplierId newId() {
        return new SupplierId(UUID.randomUUID());
    }

    public static SupplierId parse(String rawValue) {
        return new SupplierId(UuidIdentifiers.parseCanonical(rawValue, "supplier ID"));
    }

    @Override
    public String toString() {
        return canonicalValue();
    }
}
