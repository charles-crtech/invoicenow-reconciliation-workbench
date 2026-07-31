package io.github.charlescrtech.invoicenow.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImportBatchOpenApiContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "openapi.yaml")
            .toAbsolutePath()
            .normalize();

    @Test
    void contractDeclaresImplementedImportPathsReplayAndSafeProblems() throws IOException {
        String openApi = Files.readString(CONTRACT);

        assertThat(openApi)
                .contains("openapi: 3.1.0")
                .contains("/api/v1/import-batches:")
                .contains("/api/v1/import-batches/{batchId}:")
                .contains("operationId: registerImportBatch")
                .contains("operationId: getImportBatch")
                .contains("Idempotency-Key")
                .contains("Idempotent-Replay")
                .contains("IMPORT_IDEMPOTENCY_CONFLICT")
                .contains("additionalProperties: false")
                .contains("requestId");
    }
}
