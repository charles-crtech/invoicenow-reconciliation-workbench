package io.github.charlescrtech.invoicenow.generator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GeneratedDataset {

    private final GeneratorProfile profile;
    private final DatasetSummary summary;
    private final Map<String, GeneratedArtifact> artifacts;

    GeneratedDataset(
            GeneratorProfile profile,
            DatasetSummary summary,
            List<GeneratedArtifact> artifacts) {
        this.profile = Objects.requireNonNull(profile, "generator profile must not be null");
        this.summary = Objects.requireNonNull(summary, "dataset summary must not be null");
        LinkedHashMap<String, GeneratedArtifact> indexed = new LinkedHashMap<>();
        for (GeneratedArtifact artifact : List.copyOf(artifacts)) {
            if (indexed.put(artifact.name(), artifact) != null) {
                throw new IllegalArgumentException("generated artifact names must be unique");
            }
        }
        if (!List.copyOf(indexed.keySet()).equals(SetOrder.EXPECTED)) {
            throw new IllegalArgumentException("generator must produce the complete source-contract bundle");
        }
        this.artifacts = Map.copyOf(indexed);
    }

    public GeneratorProfile profile() {
        return profile;
    }

    public DatasetSummary summary() {
        return summary;
    }

    public List<GeneratedArtifact> artifacts() {
        return SetOrder.EXPECTED.stream().map(artifacts::get).toList();
    }

    public GeneratedArtifact artifact(String name) {
        GeneratedArtifact artifact = artifacts.get(name);
        if (artifact == null) {
            throw new IllegalArgumentException("unknown generated artifact " + name);
        }
        return artifact;
    }

    public void writeTo(Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "output directory must not be null");
        if (Files.exists(outputDirectory) && !Files.isDirectory(outputDirectory)) {
            throw new IllegalArgumentException("output path must be a directory");
        }
        Files.createDirectories(outputDirectory);
        for (GeneratedArtifact artifact : artifacts()) {
            Path target = outputDirectory.resolve(artifact.name());
            Path temporary = outputDirectory.resolve(artifact.name() + ".tmp");
            Files.write(temporary, artifact.content());
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static final class SetOrder {
        private static final List<String> EXPECTED = List.of(
                "dataset.json", "suppliers.csv", "invoices.csv", "ledger_entries.csv", "manifest.json");

        private SetOrder() {
        }
    }
}
