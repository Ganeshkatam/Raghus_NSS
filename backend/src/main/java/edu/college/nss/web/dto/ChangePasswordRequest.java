package edu.college.nss.web.dto;

import edu.college.nss.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    String currentPassword,

    @NotBlank(message = "New password is required")
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = "New password must be between 12 and 128 characters")
    @Pattern(
        regexp = PasswordPolicy.REGEX,
        message = PasswordPolicy.COMPLEXITY_MESSAGE
    )
    String newPassword
) {}
