package edu.college.nss.web.dto;

import java.util.UUID;

public record UnitStatsResponse(
    UUID unitId,
    String unitName,
    String unitNumber,
    Integer capacity,
    long activeVolunteers,
    long totalEvents,
    double totalServiceHours
) {}
