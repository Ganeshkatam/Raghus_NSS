package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotNull;

public record MembershipRequest(
    @NotNull(message = "Volunteer ID is required")
    Long volunteerId
) {}
