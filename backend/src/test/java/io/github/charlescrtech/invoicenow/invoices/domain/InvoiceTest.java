package io.github.charlescrtech.invoicenow.invoices.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T08:00:00Z");
    private static final Sha256Hash SOURCE_HASH = new Sha256Hash("a".repeat(64));

    @Test
    void sourceAndDocumentValuesNormalizeOnlyWhereIdentityPolicyAllows() {
        assertThat(SourceSystemCode.of("  erp_one ").value()).isEqualTo("ERP_ONE");
        assertThat(new SourceRecordId("  Batch/A-001  ").value()).isEqualTo("Batch/A-001");
        assertThat(new DocumentNumber(" inv-001 ").value()).isEqualTo("INV-001");
        assertThat(new Sha256Hash("A".repeat(64)).value()).isEqualTo("a".repeat(64));
    }

    @Test
    void sourceAndDocumentValuesRejectUnsupportedFormats() {
        assertThatThrownBy(() -> SourceSystemCode.of("x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRecordId("contains spaces"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentNumber("unsupported number"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Sha256Hash("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void correctionReasonIsTrimmedAndBounded() {
        assertThat(CorrectionReason.of("  Correct total from source  ").value())
                .isEqualTo("Correct total from source");
        assertThatThrownBy(() -> CorrectionReason.of("too short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectionReason.of("x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineNormalizesValuesAndAllowsMissingItemCode() {
        InvoiceLine line = line(1, null, "SGD", "100.00", "9.00", "109.00");

        assertThat(line.description()).isEqualTo("Consulting services");
        assertThat(line.itemCode()).isEmpty();
        assertThat(line.quantity()).isEqualByComparingTo("1.0000");
        assertThat(line.taxRate()).isEqualByComparingTo("0.0900");
    }

    @Test
    void lineRejectsInvalidStructureAndMixedCurrency() {
        assertThatThrownBy(() -> new InvoiceLine(
                        0, "Service", null, new BigDecimal("1.0000"), money("1.00", "SGD"),
                        money("1.00", "SGD"), TaxCategory.STANDARD_RATED, new BigDecimal("0.0900"),
                        money("0.09", "SGD"), money("1.09", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLine(
                        1, "Service", null, new BigDecimal("1.00001"), money("1.00", "SGD"),
                        money("1.00", "SGD"), TaxCategory.STANDARD_RATED, new BigDecimal("0.0900"),
                        money("0.09", "SGD"), money("1.09", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLine(
                        1, "Service", null, new BigDecimal("1000000000000000"), money("1.00", "SGD"),
                        money("1.00", "SGD"), TaxCategory.STANDARD_RATED, new BigDecimal("0.0900"),
                        money("0.09", "SGD"), money("1.09", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvoiceLine(
                        1, "Service", null, new BigDecimal("1.0000"), money("1.00", "SGD"),
                        money("1.00", "USD"), TaxCategory.STANDARD_RATED, new BigDecimal("0.0900"),
                        money("0.09", "SGD"), money("1.09", "SGD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createOrdersLinesAndStartsActiveWithoutPersistenceVersion() {
        Invoice invoice = invoice(List.of(
                line(2, "ITEM-2", "SGD", "20.00", "1.80", "21.80"),
                line(1, "ITEM-1", "SGD", "100.00", "9.00", "109.00")));

        assertThat(invoice.status()).isEqualTo(InvoiceStatus.ACTIVE);
        assertThat(invoice.version()).isEmpty();
        assertThat(invoice.lines()).extracting(InvoiceLine::lineNumber).containsExactly(1, 2);
        assertThat(invoice.lines()).isUnmodifiable();
        assertThat(invoice.lastCorrectionReason()).isEmpty();
        assertThat(invoice.voidReason()).isEmpty();
    }

    @Test
    void invoiceRequiresAtLeastOneUniquelyNumberedLine() {
        assertThatThrownBy(() -> invoice(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invoice(List.of(
                        line(1, null, "SGD", "10.00", "0.90", "10.90"),
                        line(1, null, "SGD", "20.00", "1.80", "21.80"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invoiceRequiresOneCurrencyAcrossHeaderAndLines() {
        assertThatThrownBy(() -> invoice(List.of(
                        line(1, null, "USD", "100.00", "9.00", "109.00"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invoiceAllowsTimingMismatchButRejectsBackwardDocumentDates() {
        Invoice invoice = Invoice.create(
                new InvoiceId(java.util.UUID.randomUUID()), SourceSystemCode.of("ERP_ONE"),
                new SourceRecordId("record-1"), SupplierId.newId(), new DocumentNumber("INV-001"),
                DocumentType.INVOICE, LocalDate.parse("2026-06-30"), LocalDate.parse("2026-07-01"),
                new ReportingPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01")),
                money("120.00", "SGD"), money("10.00", "SGD"), money("130.00", "SGD"),
                SOURCE_HASH, List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")), CREATED_AT);

        assertThat(invoice.reportingPeriod().contains(invoice.postingDate())).isFalse();
        assertThatThrownBy(() -> Invoice.create(
                        SourceSystemCode.of("ERP_ONE"), new SourceRecordId("record-2"), SupplierId.newId(),
                        new DocumentNumber("INV-002"), DocumentType.INVOICE, LocalDate.parse("2026-07-02"),
                        LocalDate.parse("2026-07-01"), monthlyPeriod(), money("1.00", "SGD"),
                        money("0.00", "SGD"), money("1.00", "SGD"), SOURCE_HASH,
                        List.of(line(1, null, "SGD", "1.00", "0.00", "1.00")), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void declaredMismatchIsRetainedAndDifferencesAreVisible() {
        Invoice invoice = invoice(List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")));

        assertThat(invoice.calculatedNetTotal()).isEqualTo(money("100.00", "SGD"));
        assertThat(invoice.calculatedTaxTotal()).isEqualTo(money("9.00", "SGD"));
        assertThat(invoice.calculatedGrossTotal()).isEqualTo(money("109.00", "SGD"));
        assertThat(invoice.netDifference()).isEqualTo(money("20.00", "SGD"));
        assertThat(invoice.taxDifference()).isEqualTo(money("1.00", "SGD"));
        assertThat(invoice.grossDifference()).isEqualTo(money("21.00", "SGD"));
    }

    @Test
    void correctionChangesOnlyControlledFieldsAndRecordsReason() {
        Invoice original = invoice(List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")));
        CorrectionReason reason = CorrectionReason.of("Correct totals from approved source");
        Invoice corrected = original.correct(
                original.issueDate(), original.postingDate(), original.reportingPeriod(),
                money("100.00", "SGD"), money("9.00", "SGD"), money("109.00", "SGD"),
                original.lines(), reason, CREATED_AT.plusSeconds(60));

        assertThat(corrected.id()).isEqualTo(original.id());
        assertThat(corrected.sourceSystem()).isEqualTo(original.sourceSystem());
        assertThat(corrected.sourceRecordId()).isEqualTo(original.sourceRecordId());
        assertThat(corrected.supplierId()).isEqualTo(original.supplierId());
        assertThat(corrected.documentNumber()).isEqualTo(original.documentNumber());
        assertThat(corrected.sourcePayloadHash()).isEqualTo(original.sourcePayloadHash());
        assertThat(corrected.lastCorrectionReason()).contains(reason);
        assertThat(corrected.updatedAt()).isEqualTo(CREATED_AT.plusSeconds(60));
    }

    @Test
    void correctionRequiresReasonAndMonotonicTime() {
        Invoice invoice = invoice(List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")));

        assertThatThrownBy(() -> invoice.correct(
                        invoice.issueDate(), invoice.postingDate(), invoice.reportingPeriod(),
                        invoice.declaredNetTotal(), invoice.declaredTaxTotal(), invoice.declaredGrossTotal(),
                        invoice.lines(), null, CREATED_AT.plusSeconds(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> invoice.correct(
                        invoice.issueDate(), invoice.postingDate(), invoice.reportingPeriod(),
                        invoice.declaredNetTotal(), invoice.declaredTaxTotal(), invoice.declaredGrossTotal(),
                        invoice.lines(), CorrectionReason.of("Correct source total"), CREATED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidIsTerminalAndRequiresReason() {
        CorrectionReason reason = CorrectionReason.of("Duplicate invoice confirmed");
        Invoice voided = invoice(List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")))
                .voidInvoice(reason, CREATED_AT.plusSeconds(1));

        assertThat(voided.status()).isEqualTo(InvoiceStatus.VOIDED);
        assertThat(voided.voidReason()).contains(reason);
        assertThatThrownBy(() -> voided.voidInvoice(reason, CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> voided.correct(
                        voided.issueDate(), voided.postingDate(), voided.reportingPeriod(),
                        voided.declaredNetTotal(), voided.declaredTaxTotal(), voided.declaredGrossTotal(),
                        voided.lines(), reason, CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoreRequiresConsistentLifecycleAndVersion() {
        Invoice original = invoice(List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")));

        assertThatThrownBy(() -> restore(original, InvoiceStatus.VOIDED, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(
                        original, InvoiceStatus.ACTIVE, CorrectionReason.of("Duplicate source invoice"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(original, InvoiceStatus.ACTIVE, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityAndSafeStringUseAggregateIdentity() {
        Invoice invoice = invoice(List.of(line(1, null, "SGD", "100.00", "9.00", "109.00")));
        Invoice restored = restore(invoice, InvoiceStatus.ACTIVE, null, 0);

        assertThat(restored).isEqualTo(invoice).hasSameHashCodeAs(invoice);
        assertThat(invoice.toString())
                .contains(invoice.id().toString(), "ERP_ONE", "INV-001", "ACTIVE")
                .doesNotContain(SOURCE_HASH.value());
    }

    private static Invoice invoice(List<InvoiceLine> lines) {
        return Invoice.create(
                new InvoiceId(java.util.UUID.fromString("5c97ce5f-b906-4e99-a16a-cbe9144d20d4")),
                SourceSystemCode.of("ERP_ONE"), new SourceRecordId("Batch/A-001"), SupplierId.newId(),
                new DocumentNumber("INV-001"), DocumentType.INVOICE, LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"), monthlyPeriod(), money("120.00", "SGD"),
                money("10.00", "SGD"), money("130.00", "SGD"), SOURCE_HASH, lines, CREATED_AT);
    }

    private static Invoice restore(Invoice invoice, InvoiceStatus status, CorrectionReason voidReason, long version) {
        return Invoice.restore(
                invoice.id(), invoice.sourceSystem(), invoice.sourceRecordId(), invoice.supplierId(),
                invoice.documentNumber(), invoice.documentType(), invoice.issueDate(), invoice.postingDate(),
                invoice.reportingPeriod(), invoice.declaredNetTotal(), invoice.declaredTaxTotal(),
                invoice.declaredGrossTotal(), invoice.sourcePayloadHash(), status, invoice.lines(), null,
                voidReason, version, invoice.createdAt(), invoice.updatedAt());
    }

    private static InvoiceLine line(
            int number,
            String itemCode,
            String currency,
            String net,
            String tax,
            String gross) {
        return new InvoiceLine(
                number, "  Consulting services  ", itemCode, new BigDecimal("1"), money(net, currency),
                money(net, currency), TaxCategory.STANDARD_RATED, new BigDecimal("0.09"),
                money(tax, currency), money(gross, currency));
    }

    private static Money money(String amount, String currency) {
        return Money.of(new BigDecimal(amount), currency);
    }

    private static ReportingPeriod monthlyPeriod() {
        return new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"));
    }
}
