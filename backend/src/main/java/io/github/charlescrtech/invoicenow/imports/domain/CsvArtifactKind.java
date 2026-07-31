package io.github.charlescrtech.invoicenow.imports.domain;

import java.util.Arrays;

public enum CsvArtifactKind {
    SUPPLIERS("suppliers.csv", "SUPPLIER"),
    INVOICES("invoices.csv", "INVOICE_LINE"),
    LEDGER_ENTRIES("ledger_entries.csv", "LEDGER_ENTRY");

    private final String fileName;
    private final String recordType;

    CsvArtifactKind(String fileName, String recordType) {
        this.fileName = fileName;
        this.recordType = recordType;
    }

    public String fileName() {
        return fileName;
    }

    public String recordType() {
        return recordType;
    }

    public static CsvArtifactKind fromFileName(String fileName) {
        return Arrays.stream(values())
                .filter(value -> value.fileName.equals(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported source-contract CSV filename"));
    }
}
