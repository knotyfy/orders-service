package com.orders.service.error;

import org.springframework.http.HttpStatus;

/**
 * Unchecked error thrown by services and mapped to ProblemDetail by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    /**
     * @param code    stable error code returned in the JSON body
     * @param status  HTTP status to send
     * @param message detail shown to the client
     */
    public ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /** @return machine-readable error code */
    public ErrorCode getCode() {
        return code;
    }

    /** @return HTTP status for this error */
    public HttpStatus getStatus() {
        return status;
    }
}
