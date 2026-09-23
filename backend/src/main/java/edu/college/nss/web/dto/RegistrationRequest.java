package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotNull;

public record RegistrationRequest(@NotNull Long volunteerId) {}
