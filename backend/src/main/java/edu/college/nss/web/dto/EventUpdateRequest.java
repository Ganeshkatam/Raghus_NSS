package edu.college.nss.web.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EventUpdateRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @Size(max = 50) String eventType,
    Instant startAt,
    Instant endAt,
    Instant registrationOpenAt,
    Instant registrationCloseAt,
    @Size(max = 255) String venue,
    @Positive Integer capacity
) {}
