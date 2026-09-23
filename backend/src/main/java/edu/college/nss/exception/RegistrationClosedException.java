package edu.college.nss.exception;

import org.springframework.http.HttpStatus;

public class RegistrationClosedException extends AppException {
    public RegistrationClosedException(String message) {
        super("REGISTRATION_CLOSED", HttpStatus.BAD_REQUEST, message);
    }
}
