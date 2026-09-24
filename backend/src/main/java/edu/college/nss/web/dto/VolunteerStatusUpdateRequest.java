package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotBlank;

public record VolunteerStatusUpdateRequest(
    @NotBlank(message = "Status is required")
    String status,
    String reason
) {}
