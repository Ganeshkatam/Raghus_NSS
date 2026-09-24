package edu.college.nss.web.dto;

import edu.college.nss.domain.VolunteerStatusHistory;
import java.time.Instant;
import java.util.UUID;

public record VolunteerStatusHistoryResponse(
    UUID historyId,
    UUID volunteerId,
    String fromStatus,
    String toStatus,
    String reason,
    String changedByName,
    Instant createdAt
) {
    public static VolunteerStatusHistoryResponse fromEntity(VolunteerStatusHistory h) {
        return new VolunteerStatusHistoryResponse(
            h.getHistoryId(),
            h.getVolunteer() != null ? h.getVolunteer().getVolunteerId() : null,
            h.getFromStatus(),
            h.getToStatus(),
            h.getReason(),
            h.getChangedBy() != null ? h.getChangedBy().getName() : "System",
            h.getCreatedAt()
        );
    }
}
