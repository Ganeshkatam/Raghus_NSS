package edu.college.nss.web.dto;

import jakarta.validation.constraints.Size;

public record CancelRegistrationRequest(
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    String reason
) {}
