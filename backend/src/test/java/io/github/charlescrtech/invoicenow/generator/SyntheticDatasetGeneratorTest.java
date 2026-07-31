package io.github.charlescrtech.invoicenow.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SyntheticDatasetGeneratorTest {

    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path SOURCE_CONTRACT = REPOSITORY_ROOT.resolve("contracts/source/v1");
    private static final Path SMOKE_PROFILE = REPOSITORY_ROOT.resolve("generator/profiles/smoke-v1.json");
    private static final Path COMMITTED_SMOKE = REPOSITORY_ROOT.resolve("data/smoke/v1");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SyntheticDatasetGenerator GENERATOR = new SyntheticDatasetGenerator();

    @Test
    void sameProfileProducesByteIdenticalArtifactsTotalsAndChecksums() throws IOException {
        GeneratorProfile profile = GeneratorProfile.load(SMOKE_PROFILE);

        GeneratedDataset first = GENERATOR.generate(profile);
        GeneratedDataset second = GENERATOR.generate(profile);

        assertThat(first.summary()).isEqualTo(second.summary());
        assertThat(first.artifacts()).extracting(GeneratedArtifact::name)
                .containsExactly(
                        "dataset.json", "suppliers.csv", "invoices.csv", "ledger_entries.csv", "manifest.json");
        for (GeneratedArtifact artifact : first.artifacts()) {
            GeneratedArtifact repeated = second.artifact(artifact.name());
            assertThat(repeated.content()).containsExactly(artifact.content());
            assertThat(repeated.sha256()).isEqualTo(artifact.sha256()).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void differentSeedChangesTransactionsButPreservesDeclaredShape() throws IOException {
        GeneratorProfile profile = GeneratorProfile.load(SMOKE_PROFILE);
        GeneratorProfile alternate = new GeneratorProfile(
                profile.profileVersion(), profile.profileName(), profile.datasetId(), profile.seed() + 1,
                profile.supplierCount(), profile.invoiceCount(), profile.maxLinesPerInvoice(),
                profile.reportingPeriodStart(), profile.reportingPeriodEnd(), profile.currency(),
                profile.taxRate());

        GeneratedDataset baseline = GENERATOR.generate(profile);
        GeneratedDataset changed = GENERATOR.generate(alternate);

        assertThat(changed.summary().supplierCount()).isEqualTo(baseline.summary().supplierCount());
        assertThat(changed.summary().invoiceCount()).isEqualTo(baseline.summary().invoiceCount());
        assertThat(changed.summary().ledgerEntryCount()).isEqualTo(baseline.summary().ledgerEntryCount());
        assertThat(changed.artifact("dataset.json").sha256())
                .isNotEqualTo(baseline.artifact("dataset.json").sha256());
        assertThat(changed.summary().invoiceGrossTotal()).isNotEqualByComparingTo(
                baseline.summary().invoiceGrossTotal());
    }

    @Test
    void generatedJsonCountsReferencesAndFinancialTotalsReconcile() throws IOException {
        GeneratedDataset dataset = GENERATOR.generate(GeneratorProfile.load(SMOKE_PROFILE));
        DatasetSummary summary = dataset.summary();
        JsonNode root = JSON.readTree(dataset.artifact("dataset.json").utf8Content());

        assertThat(root.path("contract_version").stringValue()).isEqualTo("1.0");
        assertThat(root.path("dataset_id").stringValue()).isEqualTo(dataset.profile().datasetId());
        assertThat(root.path("suppliers")).hasSize(summary.supplierCount());
        assertThat(root.path("invoices")).hasSize(summary.invoiceCount());
        assertThat(root.path("ledger_entries")).hasSize(summary.ledgerEntryCount());
        assertThat(summary.invoiceCount()).isEqualTo(100);
        assertThat(summary.supplierCount()).isEqualTo(10);
        assertThat(summary.ledgerEntryCount()).isEqualTo(summary.invoiceCount());
        assertThat(summary.invoiceGrossTotal())
                .isEqualByComparingTo(summary.invoiceNetTotal().add(summary.invoiceTaxTotal()))
                .isEqualByComparingTo(summary.ledgerDebitTotal());
        assertThat(summary.ledgerCreditTotal()).isEqualByComparingTo("0.00");

        Set<String> supplierCodes = new HashSet<>();
        root.path("suppliers").forEach(node -> {
            supplierCodes.add(node.path("supplier_code").stringValue());
            assertThat(node.path("registration_identifier").stringValue()).startsWith("SYNTH-");
        });
        int observedLines = 0;
        BigDecimal observedGross = BigDecimal.ZERO;
        for (JsonNode invoice : root.path("invoices")) {
            assertThat(supplierCodes).contains(invoice.path("supplier_code").stringValue());
            assertThat(LocalDate.parse(invoice.path("issue_date").stringValue()))
                    .isBeforeOrEqualTo(LocalDate.parse(invoice.path("posting_date").stringValue()));
            BigDecimal lineGross = BigDecimal.ZERO;
            for (JsonNode line : invoice.path("lines")) {
                assertThat(line.path("currency").stringValue())
                        .isEqualTo(invoice.path("currency").stringValue());
                lineGross = lineGross.add(line.path("gross_amount").decimalValue());
                observedLines++;
            }
            assertThat(lineGross).isEqualByComparingTo(invoice.path("declared_gross").decimalValue());
            observedGross = observedGross.add(invoice.path("declared_gross").decimalValue());
        }
        assertThat(observedLines).isEqualTo(summary.invoiceLineCount());
        assertThat(observedGross).isEqualByComparingTo(summary.invoiceGrossTotal());
    }

    @Test
    void generatedCsvUsesUtf8LfAndReconcilesRecordCounts() throws IOException {
        GeneratedDataset dataset = GENERATOR.generate(GeneratorProfile.load(SMOKE_PROFILE));
        JsonNode csvContract = JSON.readTree(Files.readString(SOURCE_CONTRACT.resolve("csv-contract.json")));

        assertCsv(dataset.artifact("suppliers.csv"), dataset.summary().supplierCount() + 1);
        assertCsv(dataset.artifact("invoices.csv"), dataset.summary().invoiceLineCount() + 1);
        assertCsv(dataset.artifact("ledger_entries.csv"), dataset.summary().ledgerEntryCount() + 1);
        for (JsonNode file : csvContract.path("files")) {
            List<String> columns = new java.util.ArrayList<>();
            file.path("columns").forEach(column -> columns.add(column.path("name").stringValue()));
            String actualHeader = dataset.artifact(file.path("name").stringValue())
                    .utf8Content()
                    .lines()
                    .findFirst()
                    .orElseThrow();
            assertThat(actualHeader).isEqualTo(String.join(",", columns));
        }
        assertThat(dataset.artifact("invoices.csv").utf8Content())
                .contains("\"Synthetic service, invoice 000001 line 01\"");
    }

    @Test
    void profileFileRejectsUnknownFieldsAndNonIntegralCounts(@TempDir Path temporary) throws IOException {
        String valid = Files.readString(SMOKE_PROFILE, StandardCharsets.UTF_8);
        Path unknown = temporary.resolve("unknown.json");
        Files.writeString(unknown, valid.replaceFirst("\\{", "{\n  \"unexpected\": true,"));
        Path fractional = temporary.resolve("fractional.json");
        Files.writeString(fractional, valid.replace("\"invoice_count\": 100", "\"invoice_count\": 1.5"));

        assertThatThrownBy(() -> GeneratorProfile.load(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly the supported fields");
        assertThatThrownBy(() -> GeneratorProfile.load(fractional))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice_count must be an integer");
    }

    @Test
    void profileAndGeneratedContentAreDefensivelyBounded() throws IOException {
        GeneratorProfile valid = GeneratorProfile.load(SMOKE_PROFILE);
        assertThat(valid.profileName()).isEqualTo("smoke");
        assertThat(valid.taxRate()).isEqualByComparingTo("0.0900");

        assertThatThrownBy(() -> profile(valid, 0, valid.invoiceCount(), valid.maxLinesPerInvoice()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile(valid, valid.supplierCount(), 100_001, valid.maxLinesPerInvoice()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile(valid, valid.supplierCount(), valid.invoiceCount(), 11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeneratorProfile(
                        "1.0", "smoke", "invalid ID", 1, 1, 1, 1,
                        valid.reportingPeriodStart(), valid.reportingPeriodEnd(), valid.currency(), valid.taxRate()))
                .isInstanceOf(IllegalArgumentException.class);

        GeneratedArtifact artifact = GENERATOR.generate(valid).artifact("dataset.json");
        byte[] copy = artifact.content();
        copy[0] = 0;
        assertThat(artifact.content()[0]).isEqualTo((byte) '{');
    }

    @Test
    void writesCompleteBundleAndRejectsFileAsOutputDirectory(@TempDir Path temporary) throws IOException {
        GeneratedDataset dataset = GENERATOR.generate(GeneratorProfile.load(SMOKE_PROFILE));
        Path output = temporary.resolve("generated");

        dataset.writeTo(output);

        assertThat(output).isDirectoryContaining(path -> path.getFileName().toString().equals("dataset.json"));
        try (Stream<Path> files = Files.list(output)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder(
                            "dataset.json",
                            "suppliers.csv",
                            "invoices.csv",
                            "ledger_entries.csv",
                            "manifest.json")
                    .noneMatch(name -> name.endsWith(".tmp"));
        }
        for (GeneratedArtifact artifact : dataset.artifacts()) {
            assertThat(Files.readAllBytes(output.resolve(artifact.name()))).containsExactly(artifact.content());
        }

        Path file = temporary.resolve("not-a-directory");
        Files.writeString(file, "occupied", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> dataset.writeTo(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void committedSmokeBundleExactlyMatchesRegeneration() throws IOException {
        GeneratedDataset regenerated = GENERATOR.generate(GeneratorProfile.load(SMOKE_PROFILE));

        for (GeneratedArtifact artifact : regenerated.artifacts()) {
            Path committed = COMMITTED_SMOKE.resolve(artifact.name());
            assertThat(committed).exists();
            assertThat(Files.readAllBytes(committed)).containsExactly(artifact.content());
        }
    }

    private static GeneratorProfile profile(
            GeneratorProfile source,
            int suppliers,
            int invoices,
            int maxLines) {
        return new GeneratorProfile(
                source.profileVersion(), source.profileName(), source.datasetId(), source.seed(),
                suppliers, invoices, maxLines, source.reportingPeriodStart(), source.reportingPeriodEnd(),
                Currency.getInstance("SGD"), source.taxRate());
    }

    private static void assertCsv(GeneratedArtifact artifact, int expectedLines) {
        byte[] bytes = artifact.content();
        boolean hasBom = bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
        assertThat(hasBom).isFalse();
        assertThat(artifact.utf8Content()).doesNotContain("\r").endsWith("\n");
        assertThat(artifact.utf8Content().lines()).hasSize(expectedLines);
    }
}
