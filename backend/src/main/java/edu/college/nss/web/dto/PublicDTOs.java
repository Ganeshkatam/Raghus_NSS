package edu.college.nss.web.dto;

import java.time.Instant;
import java.util.List;

public final class PublicDTOs {
    private PublicDTOs() {}

    public record PublicOverviewResponse(
        long activeDrivesCount,
        long completedDrivesCount,
        long verifiedUnitsCount,
        long publishedNoticesCount,
        List<PublicEventResponse> upcomingDrives,
        List<PublicAnnouncementResponse> recentAnnouncements
    ) {}

    public record PublicEventResponse(
        String title,
        String description,
        String eventType,
        String eventScope,
        String venue,
        Instant startAt,
        Instant endAt,
        String status,
        String organizingUnitNumber,
        String organizingUnitName
    ) {}

    public record PublicUnitResponse(
        String unitNumber,
        String unitName,
        String officerName
    ) {}

    public record PublicAnnouncementResponse(
        String title,
        String content,
        String priority,
        Instant publishedAt,
        String unitNumber,
        String unitName
    ) {}
}

