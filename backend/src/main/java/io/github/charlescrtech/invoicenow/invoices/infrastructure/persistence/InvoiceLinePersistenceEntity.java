package io.github.charlescrtech.invoicenow.invoices.infrastructure.persistence;

import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceLine;
import io.github.charlescrtech.invoicenow.invoices.domain.TaxCategory;
import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines", schema = "app")
class InvoiceLinePersistenceEntity {

    @EmbeddedId
    private InvoiceLinePersistenceId id;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "item_code", length = 64)
    private String itemCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_category", nullable = false, length = 20)
    private TaxCategory taxCategory;

    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    protected InvoiceLinePersistenceEntity() {
    }

    private InvoiceLinePersistenceEntity(
            InvoiceLinePersistenceId id,
            String description,
            String itemCode,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal netAmount,
            TaxCategory taxCategory,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal grossAmount) {
        this.id = id;
        this.description = description;
        this.itemCode = itemCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.netAmount = netAmount;
        this.taxCategory = taxCategory;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.grossAmount = grossAmount;
    }

    static InvoiceLinePersistenceEntity fromDomain(UUID invoiceId, InvoiceLine line) {
        return new InvoiceLinePersistenceEntity(
                new InvoiceLinePersistenceId(invoiceId, line.lineNumber()),
                line.description(),
                line.itemCode().orElse(null),
                line.quantity(),
                line.unitPrice().amount(),
                line.netAmount().amount(),
                line.taxCategory(),
                line.taxRate(),
                line.taxAmount().amount(),
                line.grossAmount().amount());
    }

    InvoiceLine toDomain(String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return new InvoiceLine(
                id.lineNumber(),
                description,
                itemCode,
                quantity,
                new Money(unitPrice, currency),
                new Money(netAmount, currency),
                taxCategory,
                taxRate,
                new Money(taxAmount, currency),
                new Money(grossAmount, currency));
    }
}
