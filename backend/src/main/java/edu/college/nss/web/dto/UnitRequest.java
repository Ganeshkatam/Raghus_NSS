package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record UnitRequest(
    @NotBlank(message = "Unit name is required")
    String unitName,

    @NotBlank(message = "Unit number is required")
    String unitNumber,

    UUID officerId,

    @Positive(message = "Unit capacity must be positive")
    Integer capacity
) {
    public UnitRequest(String unitName, String unitNumber, UUID officerId) {
        this(unitName, unitNumber, officerId, null);
    }
}

