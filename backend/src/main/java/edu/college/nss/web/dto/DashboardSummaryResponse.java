package edu.college.nss.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardSummaryResponse(
    String role,
    String userName,
    String userEmail,
    Map<String, Object> volunteerData,
    Map<String, Object> officerData,
    Map<String, Object> institutionalData
) {}
