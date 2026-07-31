package io.github.charlescrtech.invoicenow.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CsvImportOpenApiContractTest {

    @Test
    void contractPublishesBoundedCsvUploadAndPayloadFreeQuarantineRead() throws IOException {
        String contract = Files.readString(Path.of("..", "contracts", "openapi.yaml"));

        assertThat(contract).contains(
                "/api/v1/import-batches/{batchId}/csv:",
                "operationId: importCsvArtifact",
                "multipart/form-data:",
                "IMPORT_BATCH_STATE_CONFLICT",
                "/api/v1/import-batches/{batchId}/quarantine:",
                "operationId: listImportQuarantine",
                "maxItems: 100",
                "sourcePayloadHash:");
        assertThat(contract.substring(contract.indexOf("QuarantineRecord:")))
                .doesNotContain("originalRecord", "original_record");
    }
}
