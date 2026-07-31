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
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsvImportApiIntegrationTest {

    private static final String HEADER =
            "contract_version,dataset_id,supplier_code,display_name,registration_identifier,gst_registered,status";

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
    void analystImportsAndReviewerReadsSafeQuarantineMetadata() throws Exception {
        byte[] bytes = (HEADER + "\n"
                + "1.0,api-quarantine,SUP-001,Sensitive Marker,REAL-UEN-FORBIDDEN,true,ACTIVE\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration batch = register("api-quarantine", bytes, "api-quarantine-key");
        MockMultipartFile file = new MockMultipartFile(
                "file", "suppliers.csv", "text/csv", bytes);

        mvc.perform(multipart("/api/v1/import-batches/{id}/csv", batch.batch().id())
                        .file(file)
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.quarantinedCount").value(1));

        mvc.perform(get("/api/v1/import-batches/{id}/quarantine", batch.batch().id())
                        .param("limit", "10")
                        .with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].reasonCode")
                        .value("CONTRACT_SYNTHETIC_ID_REQUIRED"))
                .andExpect(jsonPath("$.items[0].fieldName").value("registration_identifier"))
                .andExpect(content().string(not(containsString("REAL-UEN-FORBIDDEN"))))
                .andExpect(content().string(not(containsString("Sensitive Marker"))));
    }

    @Test
    void reviewerCannotImportAndUnauthenticatedCallerCannotReadOrWrite() throws Exception {
        byte[] bytes = valid("api-security");
        ImportBatchRegistration batch = register("api-security", bytes, "api-security-key01");
        MockMultipartFile file = new MockMultipartFile(
                "file", "suppliers.csv", "text/csv", bytes);

        mvc.perform(multipart("/api/v1/import-batches/{id}/csv", batch.batch().id())
                        .file(file)
                        .with(user("reviewer").roles("REVIEWER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/import-batches/{id}/csv", batch.batch().id())
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/import-batches/{id}/quarantine", batch.batch().id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checksumMismatchReturnsSafeProblemAndKeepsBatchRegistered() throws Exception {
        byte[] registered = valid("api-checksum");
        byte[] changed = new String(registered, StandardCharsets.UTF_8)
                .replace("Synthetic Supplier", "Synthetic ChangedX")
                .getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration batch = register("api-checksum", registered, "api-checksum-key1");
        MockMultipartFile file = new MockMultipartFile(
                "file", "suppliers.csv", "text/csv", changed);

        mvc.perform(multipart("/api/v1/import-batches/{id}/csv", batch.batch().id())
                        .file(file)
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
    void fatalHeaderFailureReturnsStableSafeProblemAndTerminalBatch() throws Exception {
        byte[] bytes = "wrong,header\nvalue,value\n".getBytes(StandardCharsets.UTF_8);
        ImportBatchRegistration batch = register("api-fatal", bytes, "api-fatal-key-001");
        MockMultipartFile file = new MockMultipartFile(
                "file", "suppliers.csv", "text/csv", bytes);

        mvc.perform(multipart("/api/v1/import-batches/{id}/csv", batch.batch().id())
                        .file(file)
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_CSV_HEADER"))
                .andExpect(content().string(not(containsString("wrong,header"))));

        mvc.perform(get("/api/v1/import-batches/{id}", batch.batch().id())
                        .with(user("analyst").roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void missingFileAndExcessiveQuarantinePageAreRejectedWithStableRequestProblem() throws Exception {
        byte[] bytes = valid("api-bounds");
        ImportBatchRegistration batch = register("api-bounds", bytes, "api-bounds-key-01");

        mvc.perform(multipart("/api/v1/import-batches/{id}/csv", batch.batch().id())
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_REQUEST_INVALID"));

        mvc.perform(get("/api/v1/import-batches/{id}/quarantine", batch.batch().id())
                        .param("limit", "101")
                        .with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_REQUEST_INVALID"));
    }

    private ImportBatchRegistration register(String datasetId, byte[] bytes, String key) {
        return batches.register(new RegisterImportBatchCommand(
                datasetId,
                "1.0",
                ImportSourceType.CSV,
                "suppliers.csv",
                "text/csv",
                bytes.length,
                sha(bytes),
                "f".repeat(64),
                key));
    }

    private static byte[] valid(String datasetId) {
        return (HEADER + "\n1.0," + datasetId
                + ",SUP-001,Synthetic Supplier,SYNTH-UEN-000001,true,ACTIVE\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
