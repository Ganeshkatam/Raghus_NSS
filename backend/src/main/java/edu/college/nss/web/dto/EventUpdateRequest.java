package edu.college.nss.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventUpdateRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @Size(max = 50) String eventType,
    Instant startAt,
    Instant endAt,
    Instant registrationOpenAt,
    Instant registrationCloseAt,
    @Size(max = 255) String venue,
    @Positive Integer capacity,
    @Pattern(regexp = "UNIT|MULTI_UNIT|COLLEGE_WIDE", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Event scope must be UNIT, MULTI_UNIT, or COLLEGE_WIDE")
    String eventScope,
    List<UUID> participatingUnitIds,
    List<UUID> unitIds
) {
    public EventUpdateRequest(String title, String description, String eventType,
                              Instant startAt, Instant endAt, Instant registrationOpenAt,
                              Instant registrationCloseAt, String venue, Integer capacity) {
        this(title, description, eventType, startAt, endAt, registrationOpenAt, registrationCloseAt, venue, capacity, null, null, null);
    }

    public List<UUID> resolvedParticipatingUnitIds() {
        if (participatingUnitIds != null) return participatingUnitIds;
        if (unitIds != null) return unitIds;
        return null;
    }
}
