package io.github.charlescrtech.invoicenow.shared.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An immutable monetary amount normalized to its ISO currency's minor-unit
 * scale. Construction is lossless; operations that can require rounding make
 * the rounding mode explicit.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        int scale = scaleOf(currency);
        try {
            amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "amount exceeds the supported scale of " + scale + " for " + currency.getCurrencyCode(),
                    exception);
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currency(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public static Money rounded(BigDecimal amount, String currencyCode, RoundingMode roundingMode) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");

        Currency currency = currency(currencyCode);
        return new Money(amount.setScale(scaleOf(currency), roundingMode), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multipliedBy(BigDecimal factor, RoundingMode roundingMode) {
        Objects.requireNonNull(factor, "factor must not be null");
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");

        BigDecimal product = amount.multiply(factor).setScale(scaleOf(currency), roundingMode);
        return new Money(product, currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public Money absolute() {
        return amount.signum() < 0 ? negate() : this;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("money currencies must match");
        }
    }

    private static Currency currency(String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currencyCode must be a supported ISO 4217 code", exception);
        }
    }

    private static int scaleOf(Currency currency) {
        int scale = currency.getDefaultFractionDigits();
        if (scale < 0) {
            throw new IllegalArgumentException(
                    "currency must define an ISO 4217 minor-unit scale: " + currency.getCurrencyCode());
        }
        return scale;
    }
}
