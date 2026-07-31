package io.github.charlescrtech.invoicenow.imports.domain;

import java.util.Objects;
import java.util.UUID;

public record ImportBatchId(UUID value) {

    public ImportBatchId {
        Objects.requireNonNull(value, "import batch ID must not be null");
    }

    public static ImportBatchId newId() {
        return new ImportBatchId(UUID.randomUUID());
    }

    public static ImportBatchId parse(String value) {
        try {
            return new ImportBatchId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("import batch ID must be a UUID", exception);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
