package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MembershipRequest(
    @NotNull(message = "Volunteer ID is required")
    UUID volunteerId
) {}
