package io.github.charlescrtech.invoicenow.imports.infrastructure.http;

import io.github.charlescrtech.invoicenow.imports.application.ImportBatchRegistration;
import io.github.charlescrtech.invoicenow.imports.application.ImportBatchService;
import io.github.charlescrtech.invoicenow.imports.application.RegisterImportBatchCommand;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatch;
import io.github.charlescrtech.invoicenow.imports.domain.ImportBatchId;
import io.github.charlescrtech.invoicenow.imports.domain.ImportSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/import-batches", produces = MediaType.APPLICATION_JSON_VALUE)
public class ImportBatchController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";

    private final ImportBatchService service;

    ImportBatchController(ImportBatchService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ANALYST')")
    ResponseEntity<ImportBatchResponse> register(
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody RegisterImportBatchRequest request) {
        ImportBatchRegistration registration = service.register(request.toCommand(idempotencyKey));
        URI location = URI.create("/api/v1/import-batches/" + registration.batch().id());
        HttpStatus status = registration.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header(HttpHeaders.LOCATION, location.toString())
                .header(IDEMPOTENT_REPLAY, Boolean.toString(registration.replayed()))
                .body(ImportBatchResponse.from(registration));
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('ANALYST', 'REVIEWER', 'ADMIN')")
    ImportBatchResponse get(@PathVariable String batchId) {
        return ImportBatchResponse.from(service.get(ImportBatchId.parse(batchId)), false);
    }

    record RegisterImportBatchRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,63}") String datasetId,
            @NotBlank @Pattern(regexp = "1\\.0") String contractVersion,
            @NotNull ImportSourceType sourceType,
            @NotBlank @Size(max = 255) String sourceName,
            @NotBlank @Size(max = 100) String contentType,
            @Min(1) @Max(ImportBatch.MAX_SOURCE_SIZE_BYTES) long sourceSizeBytes,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sourceSha256,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String manifestSha256) {

        RegisterImportBatchCommand toCommand(String idempotencyKey) {
            return new RegisterImportBatchCommand(
                    datasetId,
                    contractVersion,
                    sourceType,
                    sourceName,
                    contentType,
                    sourceSizeBytes,
                    sourceSha256,
                    manifestSha256,
                    idempotencyKey);
        }
    }

    record ImportBatchResponse(
            String id,
            String datasetId,
            String contractVersion,
            ImportSourceType sourceType,
            String sourceName,
            String contentType,
            long sourceSizeBytes,
            String sourceSha256,
            String manifestSha256,
            String status,
            long acceptedCount,
            long rejectedCount,
            long quarantinedCount,
            Instant createdAt,
            long version,
            boolean replayed) {

        static ImportBatchResponse from(ImportBatchRegistration registration) {
            return from(registration.batch(), registration.replayed());
        }

        static ImportBatchResponse from(ImportBatch batch, boolean replayed) {
            return new ImportBatchResponse(
                    batch.id().toString(),
                    batch.datasetId(),
                    batch.contractVersion(),
                    batch.sourceType(),
                    batch.sourceName(),
                    batch.contentType(),
                    batch.sourceSizeBytes(),
                    batch.sourceSha256().value(),
                    batch.manifestSha256().value(),
                    batch.status().name(),
                    batch.acceptedCount(),
                    batch.rejectedCount(),
                    batch.quarantinedCount(),
                    batch.createdAt(),
                    batch.version().orElse(0L),
                    replayed);
        }
    }
}
