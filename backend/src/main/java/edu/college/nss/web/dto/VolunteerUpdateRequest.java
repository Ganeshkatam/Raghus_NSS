package edu.college.nss.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record VolunteerUpdateRequest(
    String name,
    String phone,
    String department,

    @Min(value = 1, message = "Year must be at least 1")
    @Max(value = 5, message = "Year must be at most 5")
    Integer yearOfStudy,

    String status
) {}
