package io.github.charlescrtech.invoicenow.imports.domain;

public enum ImportSourceType {
    CSV("text/csv"),
    JSON("application/json");

    private final String contentType;

    ImportSourceType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }
}
