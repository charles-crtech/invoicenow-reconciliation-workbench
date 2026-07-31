package io.github.charlescrtech.invoicenow.invoices.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class InvoiceLinePersistenceId implements Serializable {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    protected InvoiceLinePersistenceId() {
    }

    InvoiceLinePersistenceId(UUID invoiceId, Integer lineNumber) {
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;
    }

    Integer lineNumber() {
        return lineNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InvoiceLinePersistenceId that)) {
            return false;
        }
        return Objects.equals(invoiceId, that.invoiceId) && Objects.equals(lineNumber, that.lineNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invoiceId, lineNumber);
    }
}
