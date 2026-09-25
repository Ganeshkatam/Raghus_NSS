package edu.college.nss.web.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventCreateRequest(
    UUID unitId,
    UUID organizingUnitId,
    @Pattern(regexp = "UNIT|MULTI_UNIT|COLLEGE_WIDE", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Event scope must be UNIT, MULTI_UNIT, or COLLEGE_WIDE")
    String eventScope,
    List<UUID> participatingUnitIds,
    List<UUID> unitIds,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @NotBlank @Size(max = 50) String eventType,
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    Instant registrationOpenAt,
    Instant registrationCloseAt,
    @NotBlank @Size(max = 255) String venue,
    @NotNull @Positive Integer capacity
) {
    public EventCreateRequest(UUID unitId, String title, String description, String eventType,
                              Instant startAt, Instant endAt, Instant registrationOpenAt,
                              Instant registrationCloseAt, String venue, Integer capacity) {
        this(unitId, unitId, "UNIT", unitId != null ? List.of(unitId) : List.of(), unitId != null ? List.of(unitId) : List.of(),
             title, description, eventType, startAt, endAt, registrationOpenAt, registrationCloseAt, venue, capacity);
    }

    public UUID resolvedOrganizingUnitId() {
        return organizingUnitId != null ? organizingUnitId : unitId;
    }

    public String resolvedScope() {
        return (eventScope != null && !eventScope.isBlank()) ? eventScope.trim().toUpperCase() : "UNIT";
    }

    public List<UUID> resolvedParticipatingUnitIds() {
        if (participatingUnitIds != null && !participatingUnitIds.isEmpty()) {
            return participatingUnitIds;
        }
        if (unitIds != null && !unitIds.isEmpty()) {
            return unitIds;
        }
        UUID org = resolvedOrganizingUnitId();
        return org != null ? List.of(org) : List.of();
    }
}
