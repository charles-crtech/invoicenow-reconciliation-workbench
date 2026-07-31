package io.github.charlescrtech.invoicenow.generator;

import java.math.BigDecimal;
import java.util.Objects;

public record DatasetSummary(
        int supplierCount,
        int invoiceCount,
        int invoiceLineCount,
        int ledgerEntryCount,
        BigDecimal invoiceNetTotal,
        BigDecimal invoiceTaxTotal,
        BigDecimal invoiceGrossTotal,
        BigDecimal ledgerDebitTotal,
        BigDecimal ledgerCreditTotal) {

    public DatasetSummary {
        if (supplierCount < 1 || invoiceCount < 1 || invoiceLineCount < 1 || ledgerEntryCount < 1) {
            throw new IllegalArgumentException("generated counts must be positive");
        }
        Objects.requireNonNull(invoiceNetTotal, "invoice net total must not be null");
        Objects.requireNonNull(invoiceTaxTotal, "invoice tax total must not be null");
        Objects.requireNonNull(invoiceGrossTotal, "invoice gross total must not be null");
        Objects.requireNonNull(ledgerDebitTotal, "ledger debit total must not be null");
        Objects.requireNonNull(ledgerCreditTotal, "ledger credit total must not be null");
    }
}
