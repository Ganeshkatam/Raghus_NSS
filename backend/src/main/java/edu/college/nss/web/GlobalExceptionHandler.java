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
        String safeMessage = sanitizeMessage(ex.getMessage(), "The requested operation could not be completed.");
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), safeMessage));
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
        if (ex.getCause() instanceof DataAccessException || 
            ex.getCause() instanceof PersistenceException || 
            ex.getCause() instanceof SQLException) {
            ErrorResponse response = new ErrorResponse("SERVICE_UNAVAILABLE", "The NSS service is temporarily reconnecting to the database. Please try again shortly.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
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

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() != null ? ex.getName() : "parameter";
        ErrorResponse response = new ErrorResponse("INVALID_PARAMETER", "The provided value for '" + name + "' is invalid.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        ErrorResponse response = new ErrorResponse("MALFORMED_REQUEST", "The request payload is malformed or invalid.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex) {
        ErrorResponse response = new ErrorResponse("MISSING_PARAMETER", "Required parameter '" + ex.getParameterName() + "' is missing.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        ErrorResponse response = new ErrorResponse("METHOD_NOT_ALLOWED", "This HTTP method is not supported for this endpoint.");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        ErrorResponse response = new ErrorResponse("NOT_FOUND", "The requested resource could not be found.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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
        String[] technicalSignatures = {
            "sql", "jdbc", "resultset", "column", "table", "hibernate", "psql",
            "postgres", "exception", "bad value for type", "org.", "java.",
            "constraint", "syntax", "nullpointer", "index", "entity", "connection",
            "hikari", "timeout", "at edu.", "at org.", "at java.", "driver",
            "could not extract", "violates foreign key", "duplicate key"
        };
        for (String sig : technicalSignatures) {
            if (lower.contains(sig)) {
                return fallback;
            }
        }
        return message;
    }
}
