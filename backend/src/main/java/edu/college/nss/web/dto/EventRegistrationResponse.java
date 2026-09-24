package edu.college.nss.web.dto;

import edu.college.nss.domain.EventRegistration;
import java.time.Instant;
import java.util.UUID;

public record EventRegistrationResponse(
    UUID registrationId,
    UUID eventId,
    UUID volunteerId,
    String volunteerName,
    String collegeId,
    String status,
    Instant registeredAt
) {
    public static EventRegistrationResponse fromEntity(EventRegistration r) {
        return new EventRegistrationResponse(
            r.getRegistrationId(),
            r.getEvent().getEventId(),
            r.getVolunteer().getVolunteerId(),
            r.getVolunteer().getUser().getName(),
            r.getVolunteer().getCollegeId(),
            r.getStatus(),
            r.getRegisteredAt()
        );
    }
}
