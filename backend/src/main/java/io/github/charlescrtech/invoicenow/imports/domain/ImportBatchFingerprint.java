package io.github.charlescrtech.invoicenow.imports.domain;

import io.github.charlescrtech.invoicenow.shared.domain.source.Sha256Hash;
import java.util.Objects;

public record ImportBatchFingerprint(
        String datasetId,
        String contractVersion,
        ImportSourceType sourceType,
        Sha256Hash sourceSha256,
        Sha256Hash manifestSha256) {

    public ImportBatchFingerprint {
        Objects.requireNonNull(datasetId, "dataset ID must not be null");
        Objects.requireNonNull(contractVersion, "contract version must not be null");
        Objects.requireNonNull(sourceType, "source type must not be null");
        Objects.requireNonNull(sourceSha256, "source checksum must not be null");
        Objects.requireNonNull(manifestSha256, "manifest checksum must not be null");
    }
}
