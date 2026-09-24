package edu.college.nss.web.dto;

import java.util.UUID;

public record EventStatsResponse(
    UUID eventId,
    String title,
    int capacity,
    long registeredCount,
    long waitlistCount,
    long presentCount,
    long absentCount,
    double attendanceRatePercentage
) {}
