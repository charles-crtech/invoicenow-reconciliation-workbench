package io.github.charlescrtech.invoicenow;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.charlescrtech.invoicenow.imports.application.ImportBatchRegistration;
import io.github.charlescrtech.invoicenow.imports.application.ImportBatchService;
import io.github.charlescrtech.invoicenow.imports.application.RegisterImportBatchCommand;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonImportApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ImportBatchService batches;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearData() {
        jdbc.update("DELETE FROM app.import_quarantine");
        jdbc.update("DELETE FROM app.import_batches");
        jdbc.update("DELETE FROM app.invoice_lines");
        jdbc.update("DELETE FROM app.invoices");
        jdbc.update("DELETE FROM app.ledger_entries");
        jdbc.update("DELETE FROM app.suppliers");
    }

    @AfterEach
    void clearDataAfterTest() {
        clearData();
    }

    @Test
    void analystImportsAndReviewerReadsSafeJsonQuarantineMetadata() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(
                "..", "contracts", "source", "v1", "fixtures", "invalid", "json", "unknown-field.json"));
        ImportBatchRegistration batch = register(
                "invalid-unknown-field", bytes, "json-api-unknown-01");
        MockMultipartFile file = file("dataset.json", bytes);

        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file)
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.quarantinedCount").value(1));

        mvc.perform(get("/api/v1/import-batches/{id}/quarantine", batch.batch().id())
                        .with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].reasonCode").value("CONTRACT_UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.items[0].fieldName").value("unexpected"))
                .andExpect(content().string(not(containsString("closed schemas reject this field"))));
    }

    @Test
    void reviewerCannotImportAndUnauthenticatedCallerCannotImport() throws Exception {
        byte[] bytes = valid("json-api-security");
        ImportBatchRegistration batch = register(
                "json-api-security", bytes, "json-api-security01");
        MockMultipartFile file = file("dataset.json", bytes);

        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file)
                        .with(user("reviewer").roles("REVIEWER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checksumMismatchReturnsSafeProblemAndLeavesBatchRegistered() throws Exception {
        byte[] registered = valid("json-api-checksum");
        byte[] changed = new String(registered, StandardCharsets.UTF_8)
                .replace("Synthetic Supplier", "Synthetic ChangedX")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration batch = register(
                "json-api-checksum", registered, "json-api-checksum01");

        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file("dataset.json", changed))
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_SOURCE_CHECKSUM_MISMATCH"))
                .andExpect(content().string(not(containsString("ChangedX"))));

        mvc.perform(get("/api/v1/import-batches/{id}", batch.batch().id())
                        .with(user("analyst").roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void completedReplaySkipsNewPartAndMissingPartIsRejected() throws Exception {
        byte[] bytes = valid("json-api-replay");
        ImportBatchRegistration batch = register(
                "json-api-replay", bytes, "json-api-replay-01");

        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file("dataset.json", bytes))
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"));

        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file("different.json", "not-json".getBytes(StandardCharsets.UTF_8)))
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.acceptedCount").value(1));

        byte[] missingBytes = valid("json-api-missing");
        ImportBatchRegistration missing = register(
                "json-api-missing", missingBytes, "json-api-missing-01");
        mvc.perform(multipart("/api/v1/import-batches/{id}/json", missing.batch().id())
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_REQUEST_INVALID"));
    }

    @Test
    void parserLimitReturnsSafe413AndTerminalBatch() throws Exception {
        byte[] bytes = new String(valid("json-api-limit"), StandardCharsets.UTF_8)
                .replace("Synthetic Supplier", "Sensitive" + "x".repeat(4_097))
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration batch = register(
                "json-api-limit", bytes, "json-api-limit-001");

        mvc.perform(multipart("/api/v1/import-batches/{id}/json", batch.batch().id())
                        .file(file("dataset.json", bytes))
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMPORT_JSON_LIMIT_EXCEEDED"))
                .andExpect(content().string(not(containsString("Sensitive"))));

        mvc.perform(get("/api/v1/import-batches/{id}", batch.batch().id())
                        .with(user("analyst").roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    private ImportBatchRegistration register(String datasetId, byte[] bytes, String key) {
        return batches.register(new RegisterImportBatchCommand(
                datasetId,
                "1.0",
                ImportSourceType.JSON,
                "dataset.json",
                "application/json",
                bytes.length,
                sha(bytes),
                "f".repeat(64),
                key));
    }

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/json", bytes);
    }

    private static byte[] valid(String datasetId) {
        return """
                {"contract_version":"1.0","dataset_id":"%s","suppliers":[
                  {"supplier_code":"SUP-001","display_name":"Synthetic Supplier",
                   "registration_identifier":"SYNTH-UEN-000001","gst_registered":true,"status":"ACTIVE"}],
                 "invoices":[],"ledger_entries":[]}
                """.formatted(datasetId).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
