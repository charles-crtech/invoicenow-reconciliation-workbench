package io.github.charlescrtech.invoicenow.imports.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.imports.application.CsvImportException;
import io.github.charlescrtech.invoicenow.imports.application.CsvImportPlan;
import io.github.charlescrtech.invoicenow.imports.domain.IdempotencyKey;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import io.github.charlescrtech.invoicenow.imports.domain.QuarantineReason;
import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class BoundedCsvParserTest {

    private static final Path INVALID = Path.of("..", "contracts", "source", "v1", "fixtures", "invalid", "csv")
            .toAbsolutePath()
            .normalize();
    private static final String SUPPLIER_HEADER =
            "contract_version,dataset_id,supplier_code,display_name,registration_identifier,gst_registered,status";

    private final BoundedCsvParser parser = new BoundedCsvParser();

    @Test
    void streamsCrLfAndQuotedFieldsAndHashesExactLogicalRecordBytes() {
        String row = "1.0,contract-smoke-001,SUP-001,\"Synthetic \"\"Quoted\"\", Pte Ltd\","
                + "SYNTH-UEN-000001,true,ACTIVE";
        byte[] bytes = (SUPPLIER_HEADER + "\r\n" + row + "\r\n").getBytes(StandardCharsets.UTF_8);

        CsvImportPlan plan = parser.parse(oneByteChunks(bytes), batch("suppliers.csv", bytes));

        assertThat(plan.sourceSizeBytes()).isEqualTo(bytes.length);
        assertThat(plan.sourceSha256()).isEqualTo(sha(bytes));
        assertThat(plan.dataRecordCount()).isEqualTo(1);
        assertThat(plan.suppliers()).singleElement().satisfies(supplier -> {
            assertThat(supplier.displayName()).isEqualTo("Synthetic \"Quoted\", Pte Ltd");
            assertThat(supplier.source().hash()).isEqualTo(sha(row.getBytes(StandardCharsets.UTF_8)));
            assertThat(supplier.source().original()).isEqualTo(row);
        });
        assertThat(plan.rejectedRows()).isEmpty();
    }

    @Test
    void committedInvalidRowsKeepTheirStableFixtureReasons() throws IOException {
        assertReason("suppliers-live-registration.csv", QuarantineReason.CONTRACT_SYNTHETIC_ID_REQUIRED);
        assertReason("ledger-both-sided.csv", QuarantineReason.CONTRACT_LEDGER_SIDE);
        assertReason("invoices-wrong-header.csv", null);
    }

    @Test
    void currencyMismatchIsQuarantinedWithoutAbortingOtherRows() {
        String header = "contract_version,dataset_id,source_system,source_record_id,supplier_code,document_number,"
                + "document_type,issue_date,posting_date,reporting_period_start,reporting_period_end,currency,"
                + "declared_net,declared_tax,declared_gross,line_number,description,item_code,quantity,unit_price,"
                + "net_amount,tax_category,tax_rate,tax_amount,gross_amount,line_currency";
        String invalid = "1.0,contract-smoke-001,ERP_ONE,row-1,SUP-001,INV-1,INVOICE,2026-07-01,2026-07-01,"
                + "2026-07-01,2026-08-01,SGD,1.00,0.00,1.00,1,Test,,1,1.00,1.00,"
                + "ZERO_RATED,0,0.00,1.00,USD";
        byte[] bytes = (header + "\n" + invalid + "\n").getBytes(StandardCharsets.UTF_8);

        CsvImportPlan plan = parser.parse(new ByteArrayInputStream(bytes), batch("invoices.csv", bytes));

        assertThat(plan.invoiceLines()).isEmpty();
        assertThat(plan.rejectedRows()).singleElement()
                .extracting(CsvImportPlan.RejectedRow::reason)
                .isEqualTo(QuarantineReason.CONTRACT_CURRENCY_MISMATCH);
    }

    @Test
    void rejectsBomMalformedUtf8AndWrongHeaderAsFatalFileFailures() {
        byte[] valid = (SUPPLIER_HEADER + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[valid.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(valid, 0, bom, 3, valid.length);
        assertCode(bom, "suppliers.csv", "IMPORT_CSV_BOM");

        byte[] malformed = new byte[] {(byte) 0xc3, (byte) 0x28, '\n'};
        assertCode(malformed, "suppliers.csv", "IMPORT_CSV_UTF8");

        byte[] wrongHeader = "wrong,header\n".getBytes(StandardCharsets.UTF_8);
        assertCode(wrongHeader, "suppliers.csv", "CONTRACT_CSV_HEADER");
    }

    @Test
    void enforcesExactSourceSizeRecordSizeAndDataRecordBounds() {
        byte[] oneRow = (SUPPLIER_HEADER + "\n1.0,contract-smoke-001,SUP-001,Name,"
                + "SYNTH-UEN-000001,true,ACTIVE\n").getBytes(StandardCharsets.UTF_8);
        ImportBatch wrongSize = batch("suppliers.csv", oneRow, oneRow.length + 1L);
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(oneRow), wrongSize))
                .isInstanceOfSatisfying(CsvImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_SOURCE_SIZE_MISMATCH"));

        byte[] longRecord = (SUPPLIER_HEADER + "\n" + "x".repeat(130) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new BoundedCsvParser(128, 10)
                .parse(new ByteArrayInputStream(longRecord), batch("suppliers.csv", longRecord)))
                .isInstanceOfSatisfying(CsvImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_CSV_RECORD_TOO_LARGE"));

        String row = "1.0,contract-smoke-001,SUP-001,Name,SYNTH-UEN-000001,true,ACTIVE";
        byte[] tooMany = (SUPPLIER_HEADER + "\n" + row + "\n" + row + "\n")
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new BoundedCsvParser(512, 1)
                .parse(new ByteArrayInputStream(tooMany), batch("suppliers.csv", tooMany)))
                .isInstanceOfSatisfying(CsvImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IMPORT_CSV_TOO_MANY_RECORDS"));
    }

    @Test
    void fieldCountAndRequiredFieldFailuresAreRecoverableQuarantineRows() {
        String shortRow = "1.0,contract-smoke-001,SUP-001";
        String blankName = "1.0,contract-smoke-001,SUP-002,,SYNTH-UEN-000002,true,ACTIVE";
        byte[] bytes = (SUPPLIER_HEADER + "\n" + shortRow + "\n" + blankName + "\n")
                .getBytes(StandardCharsets.UTF_8);

        CsvImportPlan plan = parser.parse(new ByteArrayInputStream(bytes), batch("suppliers.csv", bytes));

        assertThat(plan.rejectedRows()).extracting(CsvImportPlan.RejectedRow::reason)
                .containsExactly(
                        QuarantineReason.CONTRACT_CSV_FIELD_COUNT,
                        QuarantineReason.CONTRACT_REQUIRED_FIELD);
    }

    private void assertReason(String name, QuarantineReason reason) throws IOException {
        byte[] bytes = Files.readAllBytes(INVALID.resolve(name));
        if (reason == null) {
            assertCode(bytes, "invoices.csv", "CONTRACT_CSV_HEADER");
            return;
        }
        String canonicalName = name.startsWith("suppliers-")
                ? "suppliers.csv"
                : name.startsWith("ledger-") ? "ledger_entries.csv" : "invoices.csv";
        String datasetId = new String(bytes, StandardCharsets.UTF_8).lines()
                .skip(1)
                .findFirst()
                .orElseThrow()
                .split(",", 3)[1];
        CsvImportPlan plan = parser.parse(
                new ByteArrayInputStream(bytes), batch(canonicalName, bytes, bytes.length, datasetId));
        assertThat(plan.rejectedRows()).singleElement()
                .extracting(CsvImportPlan.RejectedRow::reason)
                .isEqualTo(reason);
    }

    private void assertCode(byte[] bytes, String name, String code) {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(bytes), batch(name, bytes)))
                .isInstanceOfSatisfying(CsvImportException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static InputStream oneByteChunks(byte[] bytes) {
        return new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return super.read(buffer, offset, Math.min(1, length));
            }
        };
    }

    private static ImportBatch batch(String name, byte[] bytes) {
        return batch(name, bytes, bytes.length);
    }

    private static ImportBatch batch(String name, byte[] bytes, long registeredSize) {
        return batch(name, bytes, registeredSize, "contract-smoke-001");
    }

    private static ImportBatch batch(
            String name,
            byte[] bytes,
            long registeredSize,
            String datasetId) {
        return ImportBatch.register(
                ImportBatchId.newId(),
                datasetId,
                "1.0",
                ImportSourceType.CSV,
                name,
                "text/csv",
                registeredSize,
                sha(bytes),
                new Sha256Hash("b".repeat(64)),
                new IdempotencyKey("csv-test-key-0001"),
                Instant.parse("2026-07-31T00:00:00Z"));
    }

    private static Sha256Hash sha(byte[] bytes) {
        try {
            return new Sha256Hash(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
