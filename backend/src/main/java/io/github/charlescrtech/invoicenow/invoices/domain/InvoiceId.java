package io.github.charlescrtech.invoicenow.invoices.domain;

import io.github.charlescrtech.invoicenow.shared.domain.identifier.UuidIdentifier;
import io.github.charlescrtech.invoicenow.shared.domain.identifier.UuidIdentifiers;
import java.util.Objects;
import java.util.UUID;

public record InvoiceId(UUID value) implements UuidIdentifier {

    public InvoiceId {
        Objects.requireNonNull(value, "invoice ID value must not be null");
    }

    public static InvoiceId newId() {
        return new InvoiceId(UUID.randomUUID());
    }

    public static InvoiceId parse(String rawValue) {
        return new InvoiceId(UuidIdentifiers.parseCanonical(rawValue, "invoice ID"));
    }

    @Override
    public String toString() {
        return canonicalValue();
    }
}
