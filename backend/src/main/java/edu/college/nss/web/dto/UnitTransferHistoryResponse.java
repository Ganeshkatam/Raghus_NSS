package edu.college.nss.web.dto;

import edu.college.nss.domain.UnitTransferHistory;
import java.time.Instant;
import java.util.UUID;

public record UnitTransferHistoryResponse(
    UUID transferId,
    UUID volunteerId,
    UUID fromUnitId,
    String fromUnitName,
    UUID toUnitId,
    String toUnitName,
    String reason,
    String authorizedByName,
    Instant transferredAt
) {
    public static UnitTransferHistoryResponse fromEntity(UnitTransferHistory h) {
        return new UnitTransferHistoryResponse(
            h.getTransferId(),
            h.getVolunteer() != null ? h.getVolunteer().getVolunteerId() : null,
            h.getFromUnit() != null ? h.getFromUnit().getUnitId() : null,
            h.getFromUnit() != null ? h.getFromUnit().getUnitName() : "None",
            h.getToUnit() != null ? h.getToUnit().getUnitId() : null,
            h.getToUnit() != null ? h.getToUnit().getUnitName() : "Unknown",
            h.getReason(),
            h.getAuthorizedBy() != null ? h.getAuthorizedBy().getName() : "System",
            h.getTransferredAt()
        );
    }
}
