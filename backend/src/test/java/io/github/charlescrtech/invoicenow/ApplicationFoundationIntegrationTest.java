package io.github.charlescrtech.invoicenow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationFoundationIntegrationTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesApplicationAndAuditSchemas() {
        assertThat(schemaExists("app")).isTrue();
        assertThat(schemaExists("audit")).isTrue();
        assertThat(tableExists("public", "flyway_schema_history")).isTrue();
    }

    @Test
    void publicHealthReturnsOnlyBoundedServiceStatus() throws Exception {
        HttpResponse<String> response = get("/api/v1/health/public");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("application/json");
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.body()).contains("\"service\":\"invoicenow-workbench-api\"");
        assertThat(response.body()).doesNotContain("database", "username", "password", "details");
    }

    @Test
    void actuatorHealthIsPublicButDoesNotExposeDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.body()).doesNotContain("db", "diskSpace", "components");
    }

    @Test
    void unrecognizedApplicationRouteRequiresAuthentication() throws Exception {
        HttpResponse<String> response = get("/api/v1/not-a-public-route");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private boolean schemaExists(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName);
        return count != null && count == 1;
    }

    private boolean tableExists(String schemaName, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """,
                Integer.class,
                schemaName,
                tableName);
        return count != null && count == 1;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
