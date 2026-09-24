package edu.college.nss.web.dto;

import edu.college.nss.domain.UnitMembership;
import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
    UUID membershipId,
    UUID volunteerId,
    String volunteerName,
    String collegeId,
    String department,
    UUID unitId,
    String unitName,
    String unitNumber,
    Instant joinedAt,
    Instant leftAt,
    Boolean isActive
) {
    public static MembershipResponse fromEntity(UnitMembership m) {
        return new MembershipResponse(
            m.getMembershipId(),
            m.getVolunteer() != null ? m.getVolunteer().getVolunteerId() : null,
            (m.getVolunteer() != null && m.getVolunteer().getUser() != null) ? m.getVolunteer().getUser().getName() : null,
            m.getVolunteer() != null ? m.getVolunteer().getCollegeId() : null,
            m.getVolunteer() != null ? m.getVolunteer().getDepartment() : null,
            m.getUnit() != null ? m.getUnit().getUnitId() : null,
            m.getUnit() != null ? m.getUnit().getUnitName() : null,
            m.getUnit() != null ? m.getUnit().getUnitNumber() : null,
            m.getJoinedAt(),
            m.getLeftAt(),
            m.getIsActive()
        );
    }
}
