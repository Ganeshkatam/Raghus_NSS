package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class AnnouncementDTOs {

    public record CreateAnnouncementRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @NotBlank(message = "Content is required.")
        String content,

        Long unitId,

        Instant expiresAt
    ) {}

    public record AnnouncementResponse(
        Long announcementId,
        String title,
        String content,
        Long unitId,
        String unitName,
        String createdByName,
        Instant publishedAt,
        Instant expiresAt
    ) {}

    public record NotificationResponse(
        Long notificationId,
        String title,
        String message,
        Boolean isRead,
        Instant createdAt
    ) {}
}
