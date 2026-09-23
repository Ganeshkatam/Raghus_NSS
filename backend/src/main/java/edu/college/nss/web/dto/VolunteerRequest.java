package edu.college.nss.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VolunteerRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    String email,

    String phone,

    @NotBlank(message = "College ID is required")
    String collegeId,

    @NotBlank(message = "Department is required")
    String department,

    @NotNull(message = "Year of study is required")
    @Min(value = 1, message = "Year must be at least 1")
    @Max(value = 5, message = "Year must be at most 5")
    Integer yearOfStudy,

    String password
) {}
