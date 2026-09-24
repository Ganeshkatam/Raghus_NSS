package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public class AnnouncementDTOs {

    public record CreateAnnouncementRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @NotBlank(message = "Content is required.")
        String content,

        UUID unitId,

        Instant expiresAt
    ) {}

    public record AnnouncementResponse(
        UUID announcementId,
        String title,
        String content,
        UUID unitId,
        String unitName,
        String createdByName,
        Instant publishedAt,
        Instant expiresAt
    ) {}

    public record NotificationResponse(
        UUID notificationId,
        String title,
        String message,
        Boolean isRead,
        Instant createdAt
    ) {}
}
