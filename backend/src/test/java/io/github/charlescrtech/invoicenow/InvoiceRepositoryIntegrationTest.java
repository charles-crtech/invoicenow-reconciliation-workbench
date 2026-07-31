package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.invoices.application.InvoiceRepository;
import io.github.charlescrtech.invoicenow.invoices.domain.CorrectionReason;
import io.github.charlescrtech.invoicenow.invoices.domain.DocumentNumber;
import io.github.charlescrtech.invoicenow.invoices.domain.DocumentType;
import io.github.charlescrtech.invoicenow.invoices.domain.Invoice;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceId;
import io.github.charlescrtech.invoicenow.invoices.domain.InvoiceLine;
import io.github.charlescrtech.invoicenow.invoices.domain.TaxCategory;
import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
import io.github.charlescrtech.invoicenow.suppliers.application.SupplierRepository;
import io.github.charlescrtech.invoicenow.suppliers.domain.RegistrationIdentifier;
import io.github.charlescrtech.invoicenow.suppliers.domain.Supplier;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierCode;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierId;
import io.github.charlescrtech.invoicenow.suppliers.domain.SupplierName;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class InvoiceRepositoryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T08:00:00Z");

    @Autowired
    private InvoiceRepository invoices;

    @Autowired
    private SupplierRepository suppliers;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesInvoiceTablesAndNamedConstraints() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.flyway_schema_history WHERE version = '3' AND success",
                Integer.class);
        List<String> invoiceConstraints = constraintsFor("invoices");
        List<String> lineConstraints = constraintsFor("invoice_lines");

        assertThat(migrationCount).isEqualTo(1);
        assertThat(invoiceConstraints).contains(
                "invoices_pkey", "fk_invoices_supplier", "uq_invoices_source_identity",
                "uq_invoices_document_identity", "ck_invoices_source_system_format",
                "ck_invoices_source_record_id_format", "ck_invoices_document_number_format",
                "ck_invoices_document_type", "ck_invoices_date_order", "ck_invoices_reporting_period",
                "ck_invoices_currency_format", "ck_invoices_source_hash_format", "ck_invoices_status",
                "ck_invoices_correction_reason_format", "ck_invoices_void_state", "ck_invoices_version",
                "ck_invoices_timestamps");
        assertThat(lineConstraints).contains(
                "invoice_lines_pkey", "fk_invoice_lines_invoice", "ck_invoice_lines_line_number",
                "ck_invoice_lines_description_format", "ck_invoice_lines_item_code_format",
                "ck_invoice_lines_quantity", "ck_invoice_lines_tax_category", "ck_invoice_lines_tax_rate");
    }

    @Test
    void savesFindsAndUpdatesInvoiceWithOrderedLinesAndOptimisticVersion() {
        Supplier supplier = saveSupplier("SUP-I01", "SYNTH-UEN-I00001");
        Invoice created = invoice(
                supplier.id(), "record-001", "INV-001", List.of(line(2, "20.00"), line(1, "100.00")));

        Invoice saved = invoices.save(created);

        assertThat(saved.version()).hasValue(0);
        assertThat(saved.lines()).extracting(InvoiceLine::lineNumber).containsExactly(1, 2);
        assertThat(invoices.findById(saved.id())).contains(saved);
        assertThat(invoices.findBySourceIdentity(
                        SourceSystemCode.of("ERP_ONE"), new SourceRecordId("record-001")))
                .contains(saved);

        Invoice corrected = saved.correct(
                saved.issueDate(), saved.postingDate(), saved.reportingPeriod(),
                money("50.00"), money("4.50"), money("54.50"), List.of(line(3, "50.00")),
                CorrectionReason.of("Approved source correction"), CREATED_AT.plusSeconds(60));
        Invoice updated = invoices.save(corrected);

        assertThat(updated.version()).hasValue(1);
        assertThat(updated.lines()).extracting(InvoiceLine::lineNumber).containsExactly(3);
        assertThat(updated.lastCorrectionReason()).contains(CorrectionReason.of("Approved source correction"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.invoice_lines WHERE invoice_id = ?",
                Integer.class,
                saved.id().value())).isEqualTo(1);
    }

    @Test
    void persistsTerminalVoidState() {
        Supplier supplier = saveSupplier("SUP-I02", "SYNTH-UEN-I00002");
        CorrectionReason reason = CorrectionReason.of("Confirmed duplicate document");
        Invoice voided = invoice(supplier.id(), "record-002", "INV-002", List.of(line(1, "100.00")))
                .voidInvoice(reason, CREATED_AT.plusSeconds(1));

        Invoice saved = invoices.save(voided);

        assertThat(saved.voidReason()).contains(reason);
        assertThat(invoices.findById(saved.id())).get().extracting(Invoice::status)
                .isEqualTo(io.github.charlescrtech.invoicenow.invoices.domain.InvoiceStatus.VOIDED);
    }

    @Test
    void returnsEmptyForUnknownInvoice() {
        assertThat(invoices.findById(InvoiceId.newId())).isEmpty();
        assertThat(invoices.findBySourceIdentity(
                SourceSystemCode.of("ERP_ONE"), new SourceRecordId("missing"))).isEmpty();
    }

    @Test
    void databaseRejectsUnknownSupplier() {
        Invoice invoice = invoice(SupplierId.newId(), "record-fk", "INV-FK", List.of(line(1, "10.00")));

        assertThatThrownBy(() -> invoices.save(invoice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateSourceIdentity() {
        Supplier supplier = saveSupplier("SUP-I03", "SYNTH-UEN-I00003");
        invoices.save(invoice(supplier.id(), "same-record", "INV-003", List.of(line(1, "10.00"))));

        assertThatThrownBy(() -> invoices.save(
                        invoice(supplier.id(), "same-record", "INV-004", List.of(line(1, "10.00")))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateDocumentWithinSourceSupplierAndType() {
        Supplier supplier = saveSupplier("SUP-I04", "SYNTH-UEN-I00004");
        invoices.save(invoice(supplier.id(), "record-a", "INV-SAME", List.of(line(1, "10.00"))));

        assertThatThrownBy(() -> invoices.save(
                        invoice(supplier.id(), "record-b", "INV-SAME", List.of(line(1, "10.00")))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseAllowsSameDocumentForDifferentSuppliers() {
        Supplier first = saveSupplier("SUP-I05", "SYNTH-UEN-I00005");
        Supplier second = saveSupplier("SUP-I06", "SYNTH-UEN-I00006");

        Invoice one = invoices.save(invoice(first.id(), "record-first", "INV-SHARED", List.of(line(1, "10.00"))));
        Invoice two = invoices.save(invoice(second.id(), "record-second", "INV-SHARED", List.of(line(1, "10.00"))));

        assertThat(one.id()).isNotEqualTo(two.id());
    }

    @Test
    void databaseRejectsRawMalformedSourceHash() {
        Supplier supplier = saveSupplier("SUP-I07", "SYNTH-UEN-I00007");

        assertThatThrownBy(() -> insertRawInvoice(supplier.id(), "not-a-sha256"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawLineWithNonPositiveQuantity() {
        Supplier supplier = saveSupplier("SUP-I08", "SYNTH-UEN-I00008");
        Invoice saved = invoices.save(
                invoice(supplier.id(), "record-line", "INV-LINE", List.of(line(1, "10.00"))));

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO app.invoice_lines (
                            invoice_id, line_number, description, quantity, unit_price, net_amount,
                            tax_category, tax_rate, tax_amount, gross_amount
                        ) VALUES (?, 2, 'Invalid quantity', 0, 1, 1, 'STANDARD_RATED', 0.09, 0.09, 1.09)
                        """,
                        saved.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> constraintsFor(String table) {
        return jdbcTemplate.queryForList(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'app' AND table_name = ?
                """,
                String.class,
                table);
    }

    private Supplier saveSupplier(String code, String registrationIdentifier) {
        return suppliers.save(Supplier.create(
                SupplierCode.of(code), SupplierName.of("Invoice Test Supplier"),
                RegistrationIdentifier.of(registrationIdentifier), true, CREATED_AT));
    }

    private static Invoice invoice(
            SupplierId supplierId,
            String sourceRecordId,
            String documentNumber,
            List<InvoiceLine> lines) {
        return Invoice.create(
                SourceSystemCode.of("ERP_ONE"), new SourceRecordId(sourceRecordId), supplierId,
                new DocumentNumber(documentNumber), DocumentType.INVOICE, LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
                new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01")),
                money("120.00"), money("10.00"), money("130.00"), new Sha256Hash("a".repeat(64)),
                lines, CREATED_AT);
    }

    private static InvoiceLine line(int number, String net) {
        BigDecimal netAmount = new BigDecimal(net);
        BigDecimal taxAmount = netAmount.multiply(new BigDecimal("0.09"));
        BigDecimal grossAmount = netAmount.add(taxAmount);
        return new InvoiceLine(
                number, "Consulting services", "ITEM-" + number, BigDecimal.ONE, money(net), money(net),
                TaxCategory.STANDARD_RATED, new BigDecimal("0.09"),
                new Money(taxAmount, java.util.Currency.getInstance("SGD")),
                new Money(grossAmount, java.util.Currency.getInstance("SGD")));
    }

    private static Money money(String amount) {
        return Money.of(new BigDecimal(amount), "SGD");
    }

    private void insertRawInvoice(SupplierId supplierId, String sourceHash) {
        jdbcTemplate.update(
                """
                INSERT INTO app.invoices (
                    id, source_system, source_record_id, supplier_id, document_number, document_type,
                    issue_date, posting_date, reporting_period_start, reporting_period_end, currency,
                    declared_net, declared_tax, declared_gross, source_payload_hash, record_status,
                    version, created_at, updated_at
                ) VALUES (?, 'ERP_ONE', 'raw-record', ?, 'INV-RAW', 'INVOICE', ?, ?, ?, ?, 'SGD',
                    10, 0.90, 10.90, ?, 'ACTIVE', 0, ?, ?)
                """,
                UUID.randomUUID(),
                supplierId.value(),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-08-01"),
                sourceHash,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT));
    }
}
