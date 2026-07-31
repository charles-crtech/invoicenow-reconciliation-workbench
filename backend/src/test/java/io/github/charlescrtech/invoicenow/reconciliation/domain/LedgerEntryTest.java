package io.github.charlescrtech.invoicenow.reconciliation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-07-31T08:00:00Z");
    private static final LedgerEntryId ENTRY_ID =
            new LedgerEntryId(UUID.fromString("252c44c6-a66f-4225-b357-2466a704ef77"));

    @Test
    void matchingReferencesNormalizeToBoundedUppercaseValues() {
        assertThat(AccountCode.of("  ap.2100 ").value()).isEqualTo("AP.2100");
        assertThat(CounterpartyReference.of(" sup-001 ").value()).isEqualTo("SUP-001");
        assertThat(LedgerDocumentReference.of(" inv/2026-001 ").value()).isEqualTo("INV/2026-001");
    }

    @Test
    void matchingReferencesRejectUnsupportedOrOutOfRangeValues() {
        assertThatThrownBy(() -> AccountCode.of("AP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CounterpartyReference.of("contains spaces"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerDocumentReference.of("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debitEntryExposesPositiveSignedAmounts() {
        LedgerEntry entry = entry(money("109.00", "SGD"), money("0.00", "SGD"), money("9.00", "SGD"));

        assertThat(entry.side()).isEqualTo(LedgerEntrySide.DEBIT);
        assertThat(entry.signedAmount()).isEqualTo(money("109.00", "SGD"));
        assertThat(entry.signedTaxAmount()).isEqualTo(money("9.00", "SGD"));
        assertThat(entry.version()).isEmpty();
    }

    @Test
    void creditEntryExposesNegativeSignedAmounts() {
        LedgerEntry entry = entry(money("0.00", "SGD"), money("109.00", "SGD"), money("9.00", "SGD"));

        assertThat(entry.side()).isEqualTo(LedgerEntrySide.CREDIT);
        assertThat(entry.signedAmount()).isEqualTo(money("-109.00", "SGD"));
        assertThat(entry.signedTaxAmount()).isEqualTo(money("-9.00", "SGD"));
    }

    @Test
    void exactlyOneDebitOrCreditSideMustBePositive() {
        assertThatThrownBy(() -> entry(
                        money("0.00", "SGD"), money("0.00", "SGD"), money("0.00", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(
                        money("10.00", "SGD"), money("10.00", "SGD"), money("0.00", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(
                        money("-10.00", "SGD"), money("0.00", "SGD"), money("0.00", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taxMustBeNonNegativeAndAllAmountsUseOneCurrency() {
        assertThatThrownBy(() -> entry(
                        money("10.00", "SGD"), money("0.00", "SGD"), money("-1.00", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(
                        money("10.00", "SGD"), money("0.00", "USD"), money("1.00", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monetaryValuesMustFitThePersistenceRange() {
        assertThatThrownBy(() -> entry(
                        money("1000000000000000.00", "SGD"),
                        money("0.00", "SGD"),
                        money("0.00", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void postingPeriodMembershipIsVisibleButMismatchRemainsValid() {
        LedgerEntry matching = entry(
                money("109.00", "SGD"), money("0.00", "SGD"), money("9.00", "SGD"));
        LedgerEntry mismatch = LedgerEntry.create(
                ENTRY_ID, matching.sourceSystem(), matching.sourceRecordId(), matching.accountCode(),
                matching.counterpartyReference(), matching.documentReference(), LocalDate.parse("2026-08-01"),
                matching.reportingPeriod(), matching.debitAmount(), matching.creditAmount(), matching.taxAmount(),
                matching.sourcePayloadHash(), RECORDED_AT);

        assertThat(matching.postingDateMatchesReportingPeriod()).isTrue();
        assertThat(mismatch.postingDateMatchesReportingPeriod()).isFalse();
    }

    @Test
    void restoreRetainsPersistenceVersionAndRejectsNegativeVersion() {
        LedgerEntry entry = entry(money("109.00", "SGD"), money("0.00", "SGD"), money("9.00", "SGD"));
        LedgerEntry restored = restore(entry, 4);

        assertThat(restored.version()).hasValue(4);
        assertThatThrownBy(() -> restore(entry, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityUsesIdentityAndStringOmitsSourceEvidence() {
        LedgerEntry entry = entry(money("109.00", "SGD"), money("0.00", "SGD"), money("9.00", "SGD"));
        LedgerEntry restored = restore(entry, 0);

        assertThat(restored).isEqualTo(entry).hasSameHashCodeAs(entry);
        assertThat(entry.toString())
                .contains(ENTRY_ID.toString(), "AP.2100", "DEBIT", "2026-07-31")
                .doesNotContain(entry.sourceRecordId().value(), entry.sourcePayloadHash().value());
    }

    private static LedgerEntry entry(Money debit, Money credit, Money tax) {
        return LedgerEntry.create(
                ENTRY_ID, SourceSystemCode.of("LEDGER_ONE"), new SourceRecordId("row-001"),
                AccountCode.of("AP.2100"), CounterpartyReference.of("SUP-001"),
                LedgerDocumentReference.of("INV-001"), LocalDate.parse("2026-07-31"),
                new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01")),
                debit, credit, tax, new Sha256Hash("b".repeat(64)), RECORDED_AT);
    }

    private static LedgerEntry restore(LedgerEntry entry, long version) {
        return LedgerEntry.restore(
                entry.id(), entry.sourceSystem(), entry.sourceRecordId(), entry.accountCode(),
                entry.counterpartyReference(), entry.documentReference(), entry.postingDate(),
                entry.reportingPeriod(), entry.debitAmount(), entry.creditAmount(), entry.taxAmount(),
                entry.sourcePayloadHash(), version, entry.recordedAt());
    }

    private static Money money(String amount, String currency) {
        return Money.of(new BigDecimal(amount), currency);
    }
}
