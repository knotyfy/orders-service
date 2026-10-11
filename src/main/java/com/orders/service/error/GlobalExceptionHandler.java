package com.orders.service.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Maps domain and validation failures to RFC 7807 Problem Details with a stable {@code code} property.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles application errors such as missing stock, unknown ids, and idempotency conflicts.
     *
     * @param ex typed API error
     * @return ProblemDetail with HTTP status from the exception
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(problem(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    /**
     * Handles Bean Validation failures on request bodies (empty items, quantity &lt; 1, blank customerId).
     *
     * @param ex Spring validation exception
     * @return 400 VALIDATION_ERROR
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("Request is invalid");
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, detail));
    }

    /**
     * Handles a missing required header such as {@code Idempotency-Key}.
     *
     * @param ex missing-header exception
     * @return 400 VALIDATION_ERROR
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(
                problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Missing header: " + ex.getHeaderName()));
    }

    /**
     * Builds a ProblemDetail with {@code title}, {@code type} URN, and {@code code}.
     *
     * @param status HTTP status
     * @param code   machine-readable error code
     * @param detail human-readable explanation
     * @return populated ProblemDetail
     */
    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(code.name());
        pd.setType(URI.create("urn:orders:error:" + code.name()));
        pd.setProperty("code", code.name());
        return pd;
    }
}
