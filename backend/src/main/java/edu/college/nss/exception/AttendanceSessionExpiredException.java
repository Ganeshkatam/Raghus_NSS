package edu.college.nss.exception;

import org.springframework.http.HttpStatus;

public class AttendanceSessionExpiredException extends AppException {
    public AttendanceSessionExpiredException(String message) {
        super("ATTENDANCE_SESSION_EXPIRED", HttpStatus.BAD_REQUEST, message);
    }
}
