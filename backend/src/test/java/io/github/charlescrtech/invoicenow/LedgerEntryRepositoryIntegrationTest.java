package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.reconciliation.application.LedgerEntryRepository;
import io.github.charlescrtech.invoicenow.reconciliation.domain.AccountCode;
import io.github.charlescrtech.invoicenow.reconciliation.domain.CounterpartyReference;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerDocumentReference;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntry;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntryId;
import io.github.charlescrtech.invoicenow.reconciliation.domain.LedgerEntrySide;
import io.github.charlescrtech.invoicenow.shared.domain.money.Money;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceRecordId;
import io.github.charlescrtech.invoicenow.shared.domain.source.SourceSystemCode;
import io.github.charlescrtech.invoicenow.shared.domain.time.ReportingPeriod;
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
class LedgerEntryRepositoryIntegrationTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-07-31T08:00:00Z");

    @Autowired
    private LedgerEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesLedgerTableAndNamedConstraints() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.flyway_schema_history WHERE version = '4' AND success",
                Integer.class);
        List<String> constraints = jdbcTemplate.queryForList(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'app' AND table_name = 'ledger_entries'
                """,
                String.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(constraints).contains(
                "ledger_entries_pkey", "uq_ledger_entries_source_identity",
                "ck_ledger_entries_source_system_format", "ck_ledger_entries_source_record_id_format",
                "ck_ledger_entries_account_code_format", "ck_ledger_entries_counterparty_format",
                "ck_ledger_entries_document_format", "ck_ledger_entries_reporting_period",
                "ck_ledger_entries_currency_format", "ck_ledger_entries_debit_credit",
                "ck_ledger_entries_tax_amount", "ck_ledger_entries_source_hash_format",
                "ck_ledger_entries_version");
    }

    @Test
    void savesAndFindsDebitEntryWithAssignedVersion() {
        LedgerEntry saved = repository.save(entry("row-debit", "109.00", "0.00", "9.00"));

        assertThat(saved.version()).hasValue(0);
        assertThat(saved.side()).isEqualTo(LedgerEntrySide.DEBIT);
        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(repository.findBySourceIdentity(
                        SourceSystemCode.of("LEDGER_ONE"), new SourceRecordId("row-debit")))
                .contains(saved);
    }

    @Test
    void savesAndRestoresCreditEntryAndTimingMismatch() {
        LedgerEntry original = LedgerEntry.create(
                SourceSystemCode.of("LEDGER_ONE"), new SourceRecordId("row-credit"),
                AccountCode.of("AP.2100"), CounterpartyReference.of("SUP-001"),
                LedgerDocumentReference.of("INV-001"), LocalDate.parse("2026-08-01"), period(),
                money("0.00"), money("109.00"), money("9.00"), new Sha256Hash("c".repeat(64)),
                RECORDED_AT);

        LedgerEntry saved = repository.save(original);

        assertThat(saved.side()).isEqualTo(LedgerEntrySide.CREDIT);
        assertThat(saved.signedAmount()).isEqualTo(money("-109.00"));
        assertThat(saved.postingDateMatchesReportingPeriod()).isFalse();
    }

    @Test
    void returnsEmptyForUnknownEntry() {
        assertThat(repository.findById(LedgerEntryId.newId())).isEmpty();
        assertThat(repository.findBySourceIdentity(
                SourceSystemCode.of("LEDGER_ONE"), new SourceRecordId("missing"))).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateSourceIdentity() {
        repository.save(entry("same-row", "109.00", "0.00", "9.00"));

        assertThatThrownBy(() -> repository.save(entry("same-row", "20.00", "0.00", "1.80")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawBothSidedEntry() {
        assertThatThrownBy(() -> insertRaw(
                        "raw-both", "AP.2100", "SUP-001", "INV-001", LocalDate.parse("2026-07-31"),
                        LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"),
                        "10.00", "10.00", "0.00", "d".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawNegativeTax() {
        assertThatThrownBy(() -> insertRaw(
                        "raw-tax", "AP.2100", "SUP-001", "INV-001", LocalDate.parse("2026-07-31"),
                        LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"),
                        "10.00", "0.00", "-0.01", "d".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawMalformedMatchingReferenceAndHash() {
        assertThatThrownBy(() -> insertRaw(
                        "raw-reference", "bad account", "SUP-001", "INV-001",
                        LocalDate.parse("2026-07-31"), LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-08-01"), "10.00", "0.00", "0.00", "not-a-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRawEmptyReportingPeriod() {
        assertThatThrownBy(() -> insertRaw(
                        "raw-period", "AP.2100", "SUP-001", "INV-001", LocalDate.parse("2026-07-31"),
                        LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
                        "10.00", "0.00", "0.00", "d".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static LedgerEntry entry(String sourceRecordId, String debit, String credit, String tax) {
        return LedgerEntry.create(
                SourceSystemCode.of("LEDGER_ONE"), new SourceRecordId(sourceRecordId),
                AccountCode.of("AP.2100"), CounterpartyReference.of("SUP-001"),
                LedgerDocumentReference.of("INV-001"), LocalDate.parse("2026-07-31"), period(),
                money(debit), money(credit), money(tax), new Sha256Hash("c".repeat(64)), RECORDED_AT);
    }

    private static ReportingPeriod period() {
        return new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-08-01"));
    }

    private static Money money(String amount) {
        return Money.of(new BigDecimal(amount), "SGD");
    }

    private void insertRaw(
            String sourceRecordId,
            String accountCode,
            String counterpartyReference,
            String documentReference,
            LocalDate postingDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            String debit,
            String credit,
            String tax,
            String sourceHash) {
        jdbcTemplate.update(
                """
                INSERT INTO app.ledger_entries (
                    id, source_system, source_record_id, account_code, counterparty_reference,
                    document_reference, posting_date, reporting_period_start, reporting_period_end,
                    currency, debit_amount, credit_amount, tax_amount, source_payload_hash,
                    version, recorded_at
                ) VALUES (?, 'LEDGER_ONE', ?, ?, ?, ?, ?, ?, ?, 'SGD', ?, ?, ?, ?, 0, ?)
                """,
                UUID.randomUUID(), sourceRecordId, accountCode, counterpartyReference, documentReference,
                postingDate, periodStart, periodEnd, new BigDecimal(debit), new BigDecimal(credit),
                new BigDecimal(tax), sourceHash, Timestamp.from(RECORDED_AT));
    }
}
