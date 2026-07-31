package io.github.charlescrtech.invoicenow.imports.infrastructure.http;

import io.github.charlescrtech.invoicenow.imports.application.ImportBatchNotFoundException;
import io.github.charlescrtech.invoicenow.imports.application.ImportIdempotencyConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = ImportBatchController.class)
class ImportBatchApiExceptionHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @ExceptionHandler(ImportIdempotencyConflictException.class)
    ProblemDetail conflict(ImportIdempotencyConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "IMPORT_IDEMPOTENCY_CONFLICT",
                "Import registration conflict",
                "The idempotency key is already bound to another request.",
                request);
    }

    @ExceptionHandler(ImportBatchNotFoundException.class)
    ProblemDetail notFound(ImportBatchNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "IMPORT_BATCH_NOT_FOUND",
                "Import batch not found",
                "The requested import batch does not exist.",
                request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class
    })
    ProblemDetail invalid(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "IMPORT_REQUEST_INVALID",
                "Invalid import registration",
                "The import registration contains an invalid or unsupported value.",
                request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://invoicenow-workbench.example/problems/" + code.toLowerCase()));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId(request));
        return problem;
    }

    private static String requestId(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{8,128}")) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
