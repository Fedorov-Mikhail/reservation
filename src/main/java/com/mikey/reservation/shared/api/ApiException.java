package com.mikey.reservation.shared.api;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
    public static ApiException notFound(String kind) {
        return new ApiException(HttpStatus.NOT_FOUND, kind + "_NOT_FOUND",
                kind.equals("RESOURCE") ? "Resource not found." : "Booking not found.");
    }
}

