package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegistrationRequest(
    @NotNull UUID volunteerId,
    Boolean joinWaitlist
) {
    public RegistrationRequest(UUID volunteerId) {
        this(volunteerId, false);
    }
}
