package edu.college.nss.web.dto;

import edu.college.nss.domain.Event;
import edu.college.nss.domain.NssUnit;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record EventResponse(
    UUID eventId,
    UUID unitId,
    String unitName,
    UUID organizingUnitId,
    String organizingUnitName,
    String eventScope,
    List<EventUnitResponse> participatingUnits,
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
        UUID orgUnitId = e.getUnit() != null ? e.getUnit().getUnitId() : null;
        String orgUnitName = e.getUnit() != null ? e.getUnit().getUnitName() : null;
        String scope = e.getEventScope() != null ? e.getEventScope() : "UNIT";
        List<EventUnitResponse> partUnits = e.getParticipatingUnits() != null
            ? e.getParticipatingUnits().stream()
                .sorted(Comparator.comparing(NssUnit::getUnitNumber, Comparator.nullsLast(String::compareTo)))
                .map(u -> new EventUnitResponse(u.getUnitId(), u.getUnitName(), u.getUnitNumber()))
                .toList()
            : List.of();

        return new EventResponse(
            e.getEventId(),
            orgUnitId,
            orgUnitName,
            orgUnitId,
            orgUnitName,
            scope,
            partUnits,
            e.getCreatedBy() != null ? e.getCreatedBy().getUserId() : null,
            e.getCreatedBy() != null ? e.getCreatedBy().getName() : null,
            e.getTitle(),
            e.getDescription(),
            e.getEventType(),
            e.getStartAt(),
            e.getEndAt(),
            e.getRegistrationOpenAt(),
            e.getRegistrationCloseAt(),
            e.getVenue(),
            e.getCapacity(),
            registeredCount,
            remaining,
            e.getStatus()
        );
    }
}
