package edu.college.nss.exception;

import org.springframework.http.HttpStatus;

public class DuplicateRegistrationException extends AppException {
    public DuplicateRegistrationException() {
        super("DUPLICATE_REGISTRATION", HttpStatus.CONFLICT, "Volunteer is already registered for this event.");
    }
}
