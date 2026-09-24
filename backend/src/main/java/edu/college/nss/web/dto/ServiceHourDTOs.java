package edu.college.nss.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ServiceHourDTOs {

    public record ServiceHourClaimRequest(
        @NotNull(message = "Hours value is required.")
        @DecimalMin(value = "0.25", message = "Minimum claimable hours is 0.25.")
        @DecimalMax(value = "24.00", message = "Maximum single claim is 24.00 hours.")
        BigDecimal hours,

        UUID eventId,

        @NotBlank(message = "Activity description is required.")
        @Size(max = 255, message = "Description must not exceed 255 characters.")
        String description,

        String category,

        @Size(max = 1000, message = "Evidence note must not exceed 1000 characters.")
        String evidenceNote,

        LocalDate activityDate
    ) {
        public ServiceHourClaimRequest(BigDecimal hours, UUID eventId, String description) {
            this(hours, eventId, description, "REGULAR_ACTIVITY", null, null);
        }
    }

    public record ServiceHourReviewRequest(
        @NotBlank(message = "Action is required ('APPROVE' or 'REJECT').")
        String action,

        String reason
    ) {}

    public record ServiceHourResponse(
        UUID entryId,
        UUID volunteerId,
        String volunteerName,
        String rollNumber,
        UUID eventId,
        String eventTitle,
        BigDecimal hours,
        String status,
        String approvedByName,
        String description,
        String category,
        String evidenceNote,
        LocalDate activityDate,
        Instant createdAt
    ) {}

    public record VolunteerServiceHoursSummary(
        UUID volunteerId,
        String volunteerName,
        String rollNumber,
        BigDecimal totalApprovedHours,
        double progressPercentage,
        double regularHours,
        double communityHours,
        double otherHours,
        long approvedCount,
        long pendingCount,
        List<ServiceHourResponse> entries
    ) {}
}

