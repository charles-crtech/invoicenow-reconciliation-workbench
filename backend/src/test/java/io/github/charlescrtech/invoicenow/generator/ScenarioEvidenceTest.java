package io.github.charlescrtech.invoicenow.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.charlescrtech.invoicenow.generator.oracle.ScenarioOracle;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ScenarioEvidenceTest {

    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path PROFILE = REPOSITORY_ROOT.resolve("generator/profiles/smoke-v1.json");
    private static final Path COMMITTED = REPOSITORY_ROOT.resolve("data/smoke/v1");
    private static final Path ORACLE = Path.of("src/test/resources/oracle/smoke-v1.expected.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void publicManifestReconcilesCountsTotalsScenariosAndSourceArtifacts() throws IOException {
        GeneratedDataset dataset = new SyntheticDatasetGenerator().generate(GeneratorProfile.load(PROFILE));
        JsonNode manifest = JSON.readTree(dataset.artifact("manifest.json").utf8Content());
        DatasetSummary summary = dataset.summary();

        assertThat(manifest.path("manifest_version").stringValue()).isEqualTo("1.0");
        assertThat(manifest.path("dataset_id").stringValue()).isEqualTo(dataset.profile().datasetId());
        assertThat(manifest.path("contract_version").stringValue()).isEqualTo("1.0");
        assertThat(manifest.path("generator").path("seed").longValue()).isEqualTo(dataset.profile().seed());
        JsonNode records = manifest.path("records");
        assertThat(records.path("suppliers").intValue()).isEqualTo(summary.supplierCount());
        assertThat(records.path("invoices").intValue()).isEqualTo(summary.invoiceCount());
        assertThat(records.path("invoice_lines").intValue()).isEqualTo(summary.invoiceLineCount());
        assertThat(records.path("ledger_entries").intValue()).isEqualTo(summary.ledgerEntryCount());
        assertThat(records.path("total").intValue()).isEqualTo(
                summary.supplierCount()
                        + summary.invoiceCount()
                        + summary.invoiceLineCount()
                        + summary.ledgerEntryCount());

        JsonNode totals = manifest.path("totals");
        assertThat(totals.path("invoice_net").decimalValue()
                        .add(totals.path("invoice_tax").decimalValue()))
                .isEqualByComparingTo(totals.path("invoice_gross").decimalValue())
                .isEqualByComparingTo(totals.path("ledger_debit").decimalValue());
        assertThat(totals.path("ledger_credit").decimalValue()).isEqualByComparingTo("0.00");

        Set<String> scenarioIds = new HashSet<>();
        int coveredInvoices = 0;
        BigDecimal declaredImpact = BigDecimal.ZERO;
        for (JsonNode scenario : manifest.path("scenarios")) {
            assertThat(scenarioIds.add(scenario.path("scenario_id").stringValue())).isTrue();
            coveredInvoices += scenario.path("invoice_count").intValue();
            declaredImpact = declaredImpact.add(scenario.path("declared_financial_impact").decimalValue());
        }
        assertThat(scenarioIds).containsExactly("clean-control");
        assertThat(coveredInvoices).isEqualTo(summary.invoiceCount());
        assertThat(declaredImpact).isEqualByComparingTo("0.00");

        Set<String> indexedArtifacts = new HashSet<>();
        for (JsonNode artifactNode : manifest.path("source_artifacts")) {
            String name = artifactNode.path("name").stringValue();
            GeneratedArtifact artifact = dataset.artifact(name);
            assertThat(indexedArtifacts.add(name)).isTrue();
            assertThat(artifactNode.path("byte_count").intValue()).isEqualTo(artifact.byteCount());
            assertThat(artifactNode.path("sha256").stringValue()).isEqualTo(artifact.sha256());
            assertThat(Files.readAllBytes(COMMITTED.resolve(name))).containsExactly(artifact.content());
        }
        assertThat(indexedArtifacts)
                .containsExactlyInAnyOrder("dataset.json", "suppliers.csv", "invoices.csv", "ledger_entries.csv");
    }

    @Test
    void testOnlyOracleIsBoundToManifestAndAllExpectedOutcomesReconcile() throws IOException {
        GeneratedDataset dataset = new SyntheticDatasetGenerator().generate(GeneratorProfile.load(PROFILE));
        JsonNode manifest = JSON.readTree(dataset.artifact("manifest.json").utf8Content());
        ScenarioOracle oracle = ScenarioOracle.load(ORACLE);

        oracle.verifyBinding(manifest.path("dataset_id").stringValue(), dataset.artifact("manifest.json").sha256());
        ScenarioOracle.RecordCounts accepted = oracle.expectedImport().accepted();
        assertThat(accepted.suppliers()).isEqualTo(manifest.path("records").path("suppliers").intValue());
        assertThat(accepted.invoices()).isEqualTo(manifest.path("records").path("invoices").intValue());
        assertThat(accepted.invoiceLines()).isEqualTo(manifest.path("records").path("invoice_lines").intValue());
        assertThat(accepted.ledgerEntries()).isEqualTo(manifest.path("records").path("ledger_entries").intValue());
        assertThat(accepted.total()
                        + oracle.expectedImport().rejectedRecordCount()
                        + oracle.expectedImport().quarantinedRecordCount())
                .isEqualTo(manifest.path("records").path("total").intValue());

        assertThat(oracle.expectedReconciliation().matchedInvoiceCount()
                        + oracle.expectedReconciliation().exceptions().size())
                .isEqualTo(manifest.path("records").path("invoices").intValue());
        BigDecimal expectedExceptionImpact = oracle.expectedReconciliation().exceptions().stream()
                .map(ScenarioOracle.ExpectedException::financialImpact)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(expectedExceptionImpact)
                .isEqualByComparingTo(oracle.expectedReconciliation().unexplainedDifference());
        BigDecimal declaredImpact = BigDecimal.ZERO;
        for (JsonNode scenario : manifest.path("scenarios")) {
            declaredImpact = declaredImpact.add(scenario.path("declared_financial_impact").decimalValue());
        }
        assertThat(declaredImpact)
                .isEqualByComparingTo(oracle.expectedReconciliation().unexplainedDifference());
        assertThat(oracle.expectedReconciliation().exceptions()).isEmpty();
    }

    @Test
    void oracleRejectsUnsupportedVersionAndMismatchedManifestBinding(@TempDir Path temporary) throws IOException {
        String valid = Files.readString(ORACLE, StandardCharsets.UTF_8);
        Path unsupported = temporary.resolve("unsupported.expected.json");
        Files.writeString(
                unsupported,
                valid.replace("\"oracle_version\": \"1.0\"", "\"oracle_version\": \"2.0\""),
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ScenarioOracle.load(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oracle version must be 1.0");

        ScenarioOracle oracle = ScenarioOracle.load(ORACLE);
        assertThatThrownBy(() -> oracle.verifyBinding(oracle.datasetId(), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checksum does not match");
        assertThatThrownBy(() -> oracle.verifyBinding("different-dataset", oracle.manifestSha256()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataset ID does not match");
    }
}
