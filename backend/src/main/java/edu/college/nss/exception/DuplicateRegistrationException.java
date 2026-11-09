package edu.college.nss.exception;

import org.springframework.http.HttpStatus;

public class DuplicateRegistrationException extends AppException {
    public DuplicateRegistrationException() {
        super("DUPLICATE_REGISTRATION", HttpStatus.CONFLICT, "You have already joined this event.");
    }
}
