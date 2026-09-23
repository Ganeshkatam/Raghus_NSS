package edu.college.nss.web.dto;

import java.util.Map;

public record ErrorResponse(
    ErrorDetails error
) {
    public ErrorResponse(String code, String message) {
        this(new ErrorDetails(code, message, null));
    }

    public ErrorResponse(String code, String message, Map<String, String> details) {
        this(new ErrorDetails(code, message, details));
    }

    public record ErrorDetails(
        String code,
        String message,
        Map<String, String> details
    ) {}
}
