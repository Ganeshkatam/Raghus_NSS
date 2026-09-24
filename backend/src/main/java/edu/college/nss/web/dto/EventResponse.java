package edu.college.nss.web.dto;

import edu.college.nss.domain.Event;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
    UUID eventId,
    UUID unitId,
    String unitName,
    UUID createdBy,
    String createdByName,
    String title,
    String description,
    String eventType,
    Instant startAt,
    Instant endAt,
    Instant registrationOpenAt,
    Instant registrationCloseAt,
    String venue,
    Integer capacity,
    long registeredCount,
    long remainingCapacity,
    String status
) {
    public static EventResponse fromEntity(Event e, long registeredCount) {
        long remaining = Math.max(0, e.getCapacity() - registeredCount);
        return new EventResponse(
            e.getEventId(), e.getUnit().getUnitId(), e.getUnit().getUnitName(),
            e.getCreatedBy().getUserId(), e.getCreatedBy().getName(),
            e.getTitle(), e.getDescription(), e.getEventType(),
            e.getStartAt(), e.getEndAt(), e.getRegistrationOpenAt(), e.getRegistrationCloseAt(),
            e.getVenue(), e.getCapacity(), registeredCount, remaining, e.getStatus()
        );
    }
}
