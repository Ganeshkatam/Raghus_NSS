package edu.college.nss.web;

import edu.college.nss.exception.AppException;
import edu.college.nss.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse response = new ErrorResponse("VALIDATION_ERROR", "Invalid input parameters", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        ErrorResponse response = new ErrorResponse("AUTHENTICATION_REQUIRED", "Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(DisabledException ex) {
        ErrorResponse response = new ErrorResponse("FORBIDDEN", "Account is deactivated.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(LockedException ex) {
        ErrorResponse response = new ErrorResponse("FORBIDDEN", "Account is suspended.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        String message = sanitizeMessage(ex.getMessage(), "Access is denied.");
        ErrorResponse response = new ErrorResponse("FORBIDDEN", message);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.error("Authentication error intercepted: ", ex);
        String safeMessage = "Invalid email or password.";
        if (ex instanceof DisabledException) {
            safeMessage = "Account is deactivated.";
        } else if (ex instanceof LockedException) {
            safeMessage = "Account is suspended.";
        }
        ErrorResponse response = new ErrorResponse("AUTHENTICATION_REQUIRED", safeMessage);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler({DataAccessException.class, PersistenceException.class, SQLException.class})
    public ResponseEntity<ErrorResponse> handleDatabaseExceptions(Exception ex) {
        log.error("Database persistence exception intercepted: ", ex);
        ErrorResponse response = new ErrorResponse("SERVICE_UNAVAILABLE", "A temporary database error occurred. Please try again shortly.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = sanitizeMessage(ex.getMessage(), "Invalid request parameters.");
        ErrorResponse response = new ErrorResponse("VALIDATION_ERROR", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        String message = sanitizeMessage(ex.getMessage(), "Action cannot be completed in current state.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CONFLICT", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled internal server exception: ", ex);
        ErrorResponse response = new ErrorResponse("INTERNAL_ERROR", "An unexpected server error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String sanitizeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        String lower = message.toLowerCase();
        if (lower.contains("sql") || lower.contains("jdbc") || lower.contains("resultset") ||
            lower.contains("column") || lower.contains("table") || lower.contains("hibernate") ||
            lower.contains("psql") || lower.contains("exception") || lower.contains("bad value for type") ||
            lower.contains("org.") || lower.contains("constraint") || lower.contains("syntax")) {
            return fallback;
        }
        return message;
    }
}
