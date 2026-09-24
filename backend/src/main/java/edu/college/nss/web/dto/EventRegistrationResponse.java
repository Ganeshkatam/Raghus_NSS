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
    Integer waitlistPosition,
    String cancellationReason,
    Instant registeredAt
) {
    public static EventRegistrationResponse fromEntity(EventRegistration r) {
        return new EventRegistrationResponse(
            r.getRegistrationId(),
            r.getEvent().getEventId(),
            r.getVolunteer().getVolunteerId(),
            r.getVolunteer().getUser() != null ? r.getVolunteer().getUser().getName() : "Unknown",
            r.getVolunteer().getCollegeId(),
            r.getStatus(),
            r.getWaitlistPosition(),
            r.getCancellationReason(),
            r.getRegisteredAt()
        );
    }
}
