package io.github.charlescrtech.invoicenow.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JsonImportOpenApiContractTest {

    @Test
    void contractPublishesBoundedJsonUpload() throws IOException {
        String contract = Files.readString(Path.of("..", "contracts", "openapi.yaml"));

        assertThat(contract).contains(
                "/api/v1/import-batches/{batchId}/json:",
                "operationId: importJsonDataset",
                "Streams dataset.json with closed properties",
                "exact-byte SHA-256",
                "token, nesting, logical-record, or source-unit bound exceeded");
    }
}
