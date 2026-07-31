package io.github.charlescrtech.invoicenow.invoices.domain;

import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class InvoiceLine {

    private static final Pattern ITEM_CODE_FORMAT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,63}");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999999.9999");

    private final int lineNumber;
    private final String description;
    private final String itemCode;
    private final BigDecimal quantity;
    private final Money unitPrice;
    private final Money netAmount;
    private final TaxCategory taxCategory;
    private final BigDecimal taxRate;
    private final Money taxAmount;
    private final Money grossAmount;

    public InvoiceLine(
            int lineNumber,
            String description,
            String itemCode,
            BigDecimal quantity,
            Money unitPrice,
            Money netAmount,
            TaxCategory taxCategory,
            BigDecimal taxRate,
            Money taxAmount,
            Money grossAmount) {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("line number must be positive");
        }
        this.lineNumber = lineNumber;
        this.description = normalizeDescription(description);
        this.itemCode = normalizeItemCode(itemCode);
        this.quantity = normalizeQuantity(quantity);
        this.unitPrice = Objects.requireNonNull(unitPrice, "unit price must not be null");
        this.netAmount = Objects.requireNonNull(netAmount, "net amount must not be null");
        this.taxCategory = Objects.requireNonNull(taxCategory, "tax category must not be null");
        this.taxRate = normalizeTaxRate(taxRate);
        this.taxAmount = Objects.requireNonNull(taxAmount, "tax amount must not be null");
        this.grossAmount = Objects.requireNonNull(grossAmount, "gross amount must not be null");
        requireOneCurrency();
    }

    private static String normalizeDescription(String value) {
        Objects.requireNonNull(value, "line description must not be null");
        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || length > 500) {
            throw new IllegalArgumentException("line description must contain 1 to 500 characters");
        }
        return normalized;
    }

    private static String normalizeItemCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (!ITEM_CODE_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("item code must contain at most 64 supported characters");
        }
        return normalized;
    }

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        Objects.requireNonNull(value, "quantity must not be null");
        try {
            BigDecimal normalized = value.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            if (normalized.compareTo(MAX_QUANTITY) > 0) {
                throw new IllegalArgumentException("quantity exceeds the supported numeric range");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("quantity must have at most four decimal places", exception);
        }
    }

    private static BigDecimal normalizeTaxRate(BigDecimal value) {
        Objects.requireNonNull(value, "tax rate must not be null");
        try {
            BigDecimal normalized = value.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0 || normalized.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("tax rate must be between zero and one");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("tax rate must have at most four decimal places", exception);
        }
    }

    private void requireOneCurrency() {
        if (!unitPrice.currency().equals(netAmount.currency())
                || !unitPrice.currency().equals(taxAmount.currency())
                || !unitPrice.currency().equals(grossAmount.currency())) {
            throw new IllegalArgumentException("all invoice-line monetary values must use one currency");
        }
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String description() {
        return description;
    }

    public Optional<String> itemCode() {
        return Optional.ofNullable(itemCode);
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Money netAmount() {
        return netAmount;
    }

    public TaxCategory taxCategory() {
        return taxCategory;
    }

    public BigDecimal taxRate() {
        return taxRate;
    }

    public Money taxAmount() {
        return taxAmount;
    }

    public Money grossAmount() {
        return grossAmount;
    }
}
