package io.github.charlescrtech.invoicenow.shared.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesEquivalentValuesToTheCurrencyScale() {
        Money whole = Money.of(new BigDecimal("12"), "SGD");
        Money scaled = Money.of(new BigDecimal("12.00"), "SGD");

        assertThat(whole).isEqualTo(scaled);
        assertThat(whole.amount()).isEqualByComparingTo("12.00");
        assertThat(whole.amount().scale()).isEqualTo(2);
    }

    @Test
    void acceptsTrailingZeroScaleWithoutLoss() {
        Money money = Money.of(new BigDecimal("12.0000"), "SGD");

        assertThat(money.amount()).isEqualByComparingTo("12.00");
        assertThat(money.amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsLossyConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(new BigDecimal("12.345"), "SGD"))
                .withMessageContaining("supported scale of 2");
    }

    @Test
    void rejectsUnsupportedOrScaleLessCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(BigDecimal.ONE, "NOT-A-CURRENCY"))
                .withMessage("currencyCode must be a supported ISO 4217 code");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(BigDecimal.ONE, "XXX"))
                .withMessageContaining("must define an ISO 4217 minor-unit scale");
    }

    @Test
    void requiresAllConstructionValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(null, Currency.getInstance("SGD")))
                .withMessage("amount must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(BigDecimal.ZERO, null))
                .withMessage("currency must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of(BigDecimal.ZERO, null))
                .withMessage("currencyCode must not be null");
    }

    @Test
    void addsAndSubtractsOnlyMatchingCurrencies() {
        Money first = Money.of(new BigDecimal("10.25"), "SGD");
        Money second = Money.of(new BigDecimal("2.75"), "SGD");

        assertThat(first.plus(second)).isEqualTo(Money.of(new BigDecimal("13.00"), "SGD"));
        assertThat(first.minus(second)).isEqualTo(Money.of(new BigDecimal("7.50"), "SGD"));

        Money usd = Money.of(new BigDecimal("2.75"), "USD");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> first.plus(usd))
                .withMessage("money currencies must match");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> first.compareTo(usd))
                .withMessage("money currencies must match");
    }

    @Test
    void makesMultiplicationRoundingExplicit() {
        Money amount = Money.of(new BigDecimal("10.00"), "SGD");

        assertThat(amount.multipliedBy(new BigDecimal("0.333"), RoundingMode.HALF_UP))
                .isEqualTo(Money.of(new BigDecimal("3.33"), "SGD"));
        assertThat(amount.multipliedBy(new BigDecimal("0.335"), RoundingMode.HALF_UP))
                .isEqualTo(Money.of(new BigDecimal("3.35"), "SGD"));
        assertThat(Money.rounded(new BigDecimal("1.005"), "SGD", RoundingMode.HALF_UP))
                .isEqualTo(Money.of(new BigDecimal("1.01"), "SGD"));
    }

    @Test
    void supportsCreditsWithoutHidingTheirSign() {
        Money credit = Money.of(new BigDecimal("-12.50"), "SGD");

        assertThat(credit.isNegative()).isTrue();
        assertThat(credit.absolute()).isEqualTo(Money.of(new BigDecimal("12.50"), "SGD"));
        assertThat(credit.negate()).isEqualTo(Money.of(new BigDecimal("12.50"), "SGD"));
        assertThat(Money.zero("SGD").isZero()).isTrue();
    }
}
