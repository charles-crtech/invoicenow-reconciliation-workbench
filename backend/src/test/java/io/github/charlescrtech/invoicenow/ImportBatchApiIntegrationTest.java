package io.github.charlescrtech.invoicenow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ImportBatchApiIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearImportBatches() {
        jdbcTemplate.update("DELETE FROM app.import_batches");
    }

    @Test
    void analystCreatesReplaysAndReadsSameResource() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "client-import-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('a')))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/import-batches/")))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        JsonNode firstBody = JSON.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String id = firstBody.path("id").stringValue();

        mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "client-import-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('a')))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(get("/api/v1/import-batches/{id}", id)
                        .with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sourceSha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @Test
    void conflictingKeyReturnsSafeProblemWithoutRequestData() throws Exception {
        register('a', "client-import-0001");

        mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "client-import-0001")
                        .header("X-Request-ID", "request-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('c')))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("request-0001"))
                .andExpect(jsonPath("$.detail").value("The idempotency key is already bound to another request."))
                .andExpect(jsonPath("$.sourceSha256").doesNotExist());
    }

    @Test
    void writeRequiresAnalystAndAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("reviewer").roles("REVIEWER"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "client-import-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('a')))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/import-batches")
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "client-import-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('a')))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestAndUnknownBatchUseStableSafeProblems() throws Exception {
        mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('a').replace("application/json", "text/csv")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_REQUEST_INVALID"));

        mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, "client-import-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request('a').replace(
                                "\"manifestSha256\": \"" + "b".repeat(64) + "\"",
                                "\"manifestSha256\": \"" + "b".repeat(64) + "\",\n"
                                        + "  \"unexpected\": \"must-fail-closed\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_REQUEST_INVALID"))
                .andExpect(jsonPath("$.detail").value(
                        "The import registration contains an invalid or unsupported value."));

        mockMvc.perform(get("/api/v1/import-batches/{id}", UUID.randomUUID())
                        .with(user("analyst").roles("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_NOT_FOUND"));
    }

    private void register(char hash, String key) throws Exception {
        mockMvc.perform(post("/api/v1/import-batches")
                        .with(user("analyst").roles("ANALYST"))
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(hash)))
                .andExpect(status().isCreated());
    }

    private static String request(char sourceHash) {
        return """
                {
                  "datasetId": "smoke-v1-seed-20260731",
                  "contractVersion": "1.0",
                  "sourceType": "JSON",
                  "sourceName": "dataset.json",
                  "contentType": "application/json",
                  "sourceSizeBytes": 128279,
                  "sourceSha256": "%s",
                  "manifestSha256": "%s"
                }
                """.formatted(String.valueOf(sourceHash).repeat(64), "b".repeat(64));
    }
}
