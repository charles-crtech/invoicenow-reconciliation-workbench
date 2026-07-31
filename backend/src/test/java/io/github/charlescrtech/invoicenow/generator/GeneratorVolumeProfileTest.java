package io.github.charlescrtech.invoicenow.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GeneratorVolumeProfileTest {

    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path PROFILES = REPOSITORY_ROOT.resolve("generator/profiles");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void profilesDeclareDistinctVersionedAndBoundedVolumes() throws IOException {
        GeneratorProfile smoke = load("smoke-v1.json");
        GeneratorProfile demo = load("demo-v1.json");
        GeneratorProfile performance = load("performance-v1.json");

        assertThat(smoke.profileName()).isEqualTo("smoke");
        assertThat(demo.profileName()).isEqualTo("demo");
        assertThat(performance.profileName()).isEqualTo("performance");
        assertThat(demo.invoiceCount()).isEqualTo(10_000);
        assertThat(demo.supplierCount()).isEqualTo(250);
        assertThat(performance.invoiceCount()).isEqualTo(GeneratorProfile.MAX_INVOICES);
        assertThat(performance.supplierCount()).isEqualTo(2_000);
        assertThat(performance.maxLinesPerInvoice()).isLessThanOrEqualTo(GeneratorProfile.MAX_LINES_PER_INVOICE);
        assertThat(java.util.Set.of(smoke.datasetId(), demo.datasetId(), performance.datasetId())).hasSize(3);
        assertThat(java.util.Set.of(smoke.seed(), demo.seed(), performance.seed())).hasSize(3);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void demoProfileReproducesDeclaredVolumeAndManifest() throws IOException {
        assertGeneratedVolume(load("demo-v1.json"));
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void performanceProfileReproducesMaximumDeclaredVolumeAndManifest() throws IOException {
        assertGeneratedVolume(load("performance-v1.json"));
    }

    @Test
    void largeOutputLocationsRemainIgnoredByRepositoryPolicy() throws IOException {
        String ignoreRules = Files.readString(REPOSITORY_ROOT.resolve(".gitignore"));

        assertThat(ignoreRules).contains("data/generated/").contains("data/performance/");
    }

    private static void assertGeneratedVolume(GeneratorProfile profile) {
        GeneratedDataset dataset = new SyntheticDatasetGenerator().generate(profile);
        DatasetSummary summary = dataset.summary();

        assertThat(summary.supplierCount()).isEqualTo(profile.supplierCount());
        assertThat(summary.invoiceCount()).isEqualTo(profile.invoiceCount());
        assertThat(summary.invoiceLineCount())
                .isBetween(profile.invoiceCount(), Math.multiplyExact(
                        profile.invoiceCount(), profile.maxLinesPerInvoice()));
        assertThat(summary.ledgerEntryCount()).isEqualTo(profile.invoiceCount());
        assertThat(summary.invoiceNetTotal().add(summary.invoiceTaxTotal()))
                .isEqualByComparingTo(summary.invoiceGrossTotal())
                .isEqualByComparingTo(summary.ledgerDebitTotal());
        assertThat(summary.ledgerCreditTotal()).isEqualByComparingTo("0.00");

        JsonNode manifest = JSON.readTree(dataset.artifact("manifest.json").utf8Content());
        assertThat(manifest.path("generator").path("profile_name").stringValue())
                .isEqualTo(profile.profileName());
        assertThat(manifest.path("generator").path("seed").longValue()).isEqualTo(profile.seed());
        assertThat(manifest.path("records").path("suppliers").intValue()).isEqualTo(profile.supplierCount());
        assertThat(manifest.path("records").path("invoices").intValue()).isEqualTo(profile.invoiceCount());
        assertThat(manifest.path("records").path("invoice_lines").intValue())
                .isEqualTo(summary.invoiceLineCount());
        assertThat(manifest.path("records").path("ledger_entries").intValue())
                .isEqualTo(profile.invoiceCount());
        assertThat(manifest.path("scenarios")).hasSize(1);
        assertThat(manifest.path("scenarios").get(0).path("invoice_count").intValue())
                .isEqualTo(profile.invoiceCount());
        assertThat(manifest.path("scenarios").get(0).path("declared_financial_impact").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(manifest.path("source_artifacts")).hasSize(4);
        assertThat(dataset.artifacts()).allSatisfy(artifact -> {
            assertThat(artifact.byteCount()).isPositive();
            assertThat(artifact.sha256()).matches("[0-9a-f]{64}");
        });
    }

    private static GeneratorProfile load(String name) throws IOException {
        return GeneratorProfile.load(PROFILES.resolve(name));
    }
}
