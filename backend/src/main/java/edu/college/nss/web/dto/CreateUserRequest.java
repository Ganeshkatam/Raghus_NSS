package edu.college.nss.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name cannot exceed 150 characters")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    String email,

    String phone,

    @Size(min = 6, message = "Password must be at least 6 characters")
    String password,

    @NotBlank(message = "Role is required")
    String role,

    String status
) {}
