package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record UnitRequest(
    @NotBlank(message = "Unit name is required")
    String unitName,

    @NotBlank(message = "Unit number is required")
    String unitNumber,

    UUID officerId
) {}
