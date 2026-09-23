package edu.college.nss.web.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record EventCreateRequest(
    @NotNull Long unitId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @NotBlank @Size(max = 50) String eventType,
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    Instant registrationOpenAt,
    Instant registrationCloseAt,
    @NotBlank @Size(max = 255) String venue,
    @NotNull @Positive Integer capacity
) {}
