package io.github.charlescrtech.invoicenow.generator;

import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class ScenarioManifestArtifact {

    static final String MANIFEST_VERSION = "1.0";
    static final String CONTRACT_VERSION = "1.0";
    static final String CLEAN_SCENARIO_ID = "clean-control";

    private static final ObjectMapper JSON = new ObjectMapper();

    private ScenarioManifestArtifact() {
    }

    static GeneratedArtifact create(
            GeneratorProfile profile,
            DatasetSummary summary,
            List<GeneratedArtifact> sourceArtifacts) {
        ObjectNode root = JSON.createObjectNode();
        root.put("manifest_version", MANIFEST_VERSION);
        root.put("dataset_id", profile.datasetId());
        root.put("contract_version", CONTRACT_VERSION);

        ObjectNode generator = root.putObject("generator");
        generator.put("generator_version", SyntheticDatasetGenerator.GENERATOR_VERSION);
        generator.put("profile_version", profile.profileVersion());
        generator.put("profile_name", profile.profileName());
        generator.put("seed", profile.seed());

        ObjectNode reportingPeriod = root.putObject("reporting_period");
        reportingPeriod.put("start", profile.reportingPeriodStart().toString());
        reportingPeriod.put("end_exclusive", profile.reportingPeriodEnd().toString());
        reportingPeriod.put("currency", profile.currency().getCurrencyCode());

        ObjectNode records = root.putObject("records");
        records.put("suppliers", summary.supplierCount());
        records.put("invoices", summary.invoiceCount());
        records.put("invoice_lines", summary.invoiceLineCount());
        records.put("ledger_entries", summary.ledgerEntryCount());
        records.put("total", totalRecords(summary));

        ObjectNode totals = root.putObject("totals");
        totals.put("currency", profile.currency().getCurrencyCode());
        totals.put("invoice_net", summary.invoiceNetTotal());
        totals.put("invoice_tax", summary.invoiceTaxTotal());
        totals.put("invoice_gross", summary.invoiceGrossTotal());
        totals.put("ledger_debit", summary.ledgerDebitTotal());
        totals.put("ledger_credit", summary.ledgerCreditTotal());

        ArrayNode scenarios = root.putArray("scenarios");
        ObjectNode clean = scenarios.addObject();
        clean.put("scenario_id", CLEAN_SCENARIO_ID);
        clean.put("scenario_type", "CLEAN_CONTROL");
        clean.put("invoice_count", summary.invoiceCount());
        clean.put("declared_financial_impact", zero(profile));
        clean.put(
                "description",
                "Every generated invoice has one same-reference ledger debit for its declared gross amount.");

        ArrayNode artifacts = root.putArray("source_artifacts");
        for (GeneratedArtifact artifact : sourceArtifacts) {
            ObjectNode node = artifacts.addObject();
            node.put("name", artifact.name());
            node.put("media_type", artifact.name().endsWith(".json") ? "application/json" : "text/csv");
            node.put("byte_count", artifact.byteCount());
            node.put("sha256", artifact.sha256());
        }
        return GeneratedArtifact.utf8("manifest.json", JSON.writeValueAsString(root) + "\n");
    }

    private static int totalRecords(DatasetSummary summary) {
        return Math.addExact(
                Math.addExact(summary.supplierCount(), summary.invoiceCount()),
                Math.addExact(summary.invoiceLineCount(), summary.ledgerEntryCount()));
    }

    private static BigDecimal zero(GeneratorProfile profile) {
        return BigDecimal.ZERO.setScale(profile.monetaryScale());
    }
}
