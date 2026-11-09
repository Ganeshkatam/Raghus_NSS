package edu.college.nss.exception;

import org.springframework.http.HttpStatus;

public class DuplicateAttendanceException extends AppException {
    public DuplicateAttendanceException() {
        super("DUPLICATE_ATTENDANCE", HttpStatus.CONFLICT, "Your attendance has already been recorded for this session.");
    }
}
