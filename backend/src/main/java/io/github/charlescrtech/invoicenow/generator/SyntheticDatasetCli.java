package io.github.charlescrtech.invoicenow.generator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SyntheticDatasetCli {

    private SyntheticDatasetCli() {
    }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = parseOptions(arguments);
        GeneratorProfile profile = GeneratorProfile.load(Path.of(options.get("profile")));
        GeneratedDataset dataset = new SyntheticDatasetGenerator().generate(profile);
        Path output = Path.of(options.get("output"));
        dataset.writeTo(output);

        System.out.println("Generated " + profile.datasetId() + " with seed " + profile.seed());
        System.out.println("Suppliers: " + dataset.summary().supplierCount());
        System.out.println("Invoices: " + dataset.summary().invoiceCount());
        System.out.println("Invoice lines: " + dataset.summary().invoiceLineCount());
        System.out.println("Ledger entries: " + dataset.summary().ledgerEntryCount());
        System.out.println("Invoice gross: " + dataset.summary().invoiceGrossTotal().toPlainString());
        for (GeneratedArtifact artifact : dataset.artifacts()) {
            System.out.println(artifact.sha256() + "  " + artifact.name());
        }
    }

    private static Map<String, String> parseOptions(String[] arguments) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (String argument : arguments) {
            if (!argument.startsWith("--") || !argument.contains("=")) {
                throw usage("arguments must use --name=value");
            }
            int separator = argument.indexOf('=');
            String name = argument.substring(2, separator);
            String value = argument.substring(separator + 1);
            if (!name.equals("profile") && !name.equals("output")) {
                throw usage("unknown option --" + name);
            }
            if (value.isBlank() || options.put(name, value) != null) {
                throw usage("options must be nonblank and specified once");
            }
        }
        if (!options.keySet().equals(java.util.Set.of("profile", "output"))) {
            throw usage("both --profile and --output are required");
        }
        return Map.copyOf(options);
    }

    private static IllegalArgumentException usage(String reason) {
        return new IllegalArgumentException(
                reason + "; usage: --profile=<profile.json> --output=<directory>");
    }
}
