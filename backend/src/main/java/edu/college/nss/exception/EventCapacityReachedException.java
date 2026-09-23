package edu.college.nss.exception;

import org.springframework.http.HttpStatus;

public class EventCapacityReachedException extends AppException {
    public EventCapacityReachedException() {
        super("EVENT_CAPACITY_REACHED", HttpStatus.CONFLICT, "Event capacity has been reached.");
    }
}
