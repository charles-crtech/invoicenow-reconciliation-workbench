package io.github.charlescrtech.invoicenow.reconciliation.domain;

import io.github.charlescrtech.invoicenow.shared.domain.identifier.UuidIdentifier;
import io.github.charlescrtech.invoicenow.shared.domain.identifier.UuidIdentifiers;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntryId(UUID value) implements UuidIdentifier {

    public LedgerEntryId {
        Objects.requireNonNull(value, "ledger entry ID value must not be null");
    }

    public static LedgerEntryId newId() {
        return new LedgerEntryId(UUID.randomUUID());
    }

    public static LedgerEntryId parse(String rawValue) {
        return new LedgerEntryId(UuidIdentifiers.parseCanonical(rawValue, "ledger entry ID"));
    }

    @Override
    public String toString() {
        return canonicalValue();
    }
}
