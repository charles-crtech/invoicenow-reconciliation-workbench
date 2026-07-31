package io.github.charlescrtech.invoicenow.imports.application;

public final class ImportIdempotencyConflictException extends RuntimeException {

    public ImportIdempotencyConflictException() {
        super("idempotency key is already bound to a different import request");
    }
}
