package edu.college.nss.web.dto;

import java.math.BigDecimal;
import java.util.List;

public class ReportDTOs {

    public record InstitutionalMetricsResponse(
        long totalVolunteers,
        long activeVolunteers,
        long totalUnits,
        long totalEvents,
        long completedEvents,
        BigDecimal totalServiceHours,
        List<UnitPerformanceMetric> unitPerformance
    ) {}

    public record UnitPerformanceMetric(
        Long unitId,
        String unitName,
        String unitNumber,
        String officerName,
        long volunteerCount,
        long eventCount,
        BigDecimal totalServiceHours
    ) {}
}
