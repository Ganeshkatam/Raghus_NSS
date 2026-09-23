package edu.college.nss.web.dto;

import edu.college.nss.domain.NssUnit;
import java.time.Instant;

public record UnitResponse(
    Long unitId,
    String unitName,
    String unitNumber,
    Long officerId,
    String officerName,
    String officerEmail,
    long activeMemberCount,
    Instant createdAt
) {
    public static UnitResponse fromEntity(NssUnit unit, long activeMemberCount) {
        return new UnitResponse(
            unit.getUnitId(),
            unit.getUnitName(),
            unit.getUnitNumber(),
            unit.getOfficer() != null ? unit.getOfficer().getUserId() : null,
            unit.getOfficer() != null ? unit.getOfficer().getName() : null,
            unit.getOfficer() != null ? unit.getOfficer().getEmail() : null,
            activeMemberCount,
            unit.getCreatedAt()
        );
    }
}
