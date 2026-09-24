package edu.college.nss.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogItem(
    UUID id,
    String entityType,
    String entityName,
    String action,
    String previousState,
    String newState,
    String reason,
    String actorName,
    Instant timestamp
) {}
