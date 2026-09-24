package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UnitTransferRequest(
    @NotNull(message = "Volunteer ID is required")
    UUID volunteerId,
    @NotNull(message = "Target unit ID is required")
    UUID targetUnitId,
    String reason
) {}
