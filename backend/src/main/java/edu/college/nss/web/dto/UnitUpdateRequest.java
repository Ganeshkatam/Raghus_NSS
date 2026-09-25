package edu.college.nss.web.dto;

import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record UnitUpdateRequest(
    String unitName,
    UUID officerId,
    Boolean clearOfficer,
    @Positive(message = "Unit capacity must be positive")
    Integer capacity
) {
    public UnitUpdateRequest(String unitName, UUID officerId) {
        this(unitName, officerId, false, null);
    }

    public UnitUpdateRequest(String unitName, UUID officerId, Boolean clearOfficer) {
        this(unitName, officerId, clearOfficer, null);
    }
}


