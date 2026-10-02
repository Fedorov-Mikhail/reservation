package com.mikey.reservation.shared.api;

import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> domain(ApiException error, WebRequest request) {
        return problem(error.status(), error.code(), error.getMessage(), request);
    }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        decorate(detail, "VALIDATION_FAILED", request);
        detail.setProperty("errors", error.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage())).toList());
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }
    record FieldError(String field, String message) {}
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Do not echo rejected payloads, stack traces or implementation class names.
        var detail = ProblemDetail.forStatusAndDetail(status, "The HTTP request could not be processed.");
        decorate(detail, status.value() == 404 ? "NOT_FOUND" : "INVALID_REQUEST", request);
        return new ResponseEntity<>(detail, headers, status);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception error, WebRequest request) {
        if (temporary(error)) {
            log.warn("database_temporarily_unavailable type={}", error.getClass().getSimpleName());
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_UNAVAILABLE", "Database is temporarily unavailable.", request);
        }
        log.error("unexpected_request_failure", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
    }
    private boolean temporary(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException || cause instanceof java.sql.SQLTransientConnectionException || cause instanceof java.sql.SQLRecoverableException) return true;
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || List.of("55P03", "57014", "40P01", "40001").contains(sql.getSQLState())))
                return true;
        }
        return false;
    }
    private ResponseEntity<Object> problem(HttpStatus status, String code, String message, WebRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        decorate(detail, code, request);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }
    private void decorate(ProblemDetail detail, String code, WebRequest request) {
        detail.setType(URI.create("urn:reservation:problem:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        detail.setProperty("code", code);
        detail.setProperty("requestId", MDC.get("requestId"));
        if (request instanceof ServletWebRequest servlet) detail.setInstance(URI.create(servlet.getRequest().getRequestURI()));
    }
}


