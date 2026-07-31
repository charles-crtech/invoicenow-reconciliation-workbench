package io.github.charlescrtech.invoicenow.imports.application;

public final class ImportBatchNotFoundException extends RuntimeException {

    public ImportBatchNotFoundException() {
        super("import batch was not found");
    }
}
