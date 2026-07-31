package io.github.charlescrtech.invoicenow.imports.application;

public final class JsonImportException extends RuntimeException {

    private final String code;
    private final boolean terminal;

    public JsonImportException(String code, boolean terminal, String internalMessage) {
        super(internalMessage);
        this.code = code;
        this.terminal = terminal;
    }

    public String code() {
        return code;
    }

    public boolean terminal() {
        return terminal;
    }
}
