package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class AttendanceDTOs {

    public record CreateSessionRequest(
        @NotNull Instant startsAt,
        @NotNull Instant expiresAt
    ) {}

    public record SessionResponse(
        UUID sessionId,
        UUID eventId,
        String eventTitle,
        String openedByName,
        Instant startsAt,
        Instant expiresAt,
        String status,
        String qrToken,
        long presentCount,
        long totalRegistered
    ) {}

    public record CheckInRequest(
        @NotBlank String token
    ) {}

    public record CheckInResponse(
        UUID attendanceId,
        UUID volunteerId,
        String volunteerName,
        String rollNumber,
        Instant checkedInAt,
        String status,
        String checkInMethod,
        String message
    ) {}

    public record ManualCheckInRequest(
        @NotNull UUID volunteerId,
        @NotBlank String status
    ) {}

    public record CorrectionRequest(
        @NotBlank String newStatus,
        @NotBlank String reason
    ) {}

    public record CorrectionResponse(
        UUID correctionId,
        UUID attendanceId,
        String correctedByName,
        String previousStatus,
        String newStatus,
        String reason,
        Instant correctedAt
    ) {}

    public record AttendanceRosterItem(
        UUID volunteerId,
        String rollNumber,
        String fullName,
        String department,
        String nssUnitCode,
        String registrationStatus,
        String attendanceStatus,
        String checkInMethod,
        Instant checkedInAt,
        UUID attendanceId
    ) {}
}
