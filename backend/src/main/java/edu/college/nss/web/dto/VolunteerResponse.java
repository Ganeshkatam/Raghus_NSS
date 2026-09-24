package edu.college.nss.web.dto;

import edu.college.nss.domain.UnitMembership;
import edu.college.nss.domain.Volunteer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VolunteerResponse(
    UUID volunteerId,
    UUID userId,
    String name,
    String email,
    String phone,
    String collegeId,
    String department,
    Integer yearOfStudy,
    LocalDate joinDate,
    String status,
    UUID activeUnitId,
    String activeUnitName,
    Instant createdAt
) {
    public static VolunteerResponse fromEntity(Volunteer v, UnitMembership activeMembership) {
        UUID unitId = (activeMembership != null && activeMembership.getUnit() != null)
            ? activeMembership.getUnit().getUnitId() : null;
        String unitName = (activeMembership != null && activeMembership.getUnit() != null)
            ? activeMembership.getUnit().getUnitName() : null;

        return new VolunteerResponse(
            v.getVolunteerId(),
            v.getUser() != null ? v.getUser().getUserId() : null,
            v.getUser() != null ? v.getUser().getName() : null,
            v.getUser() != null ? v.getUser().getEmail() : null,
            v.getUser() != null ? v.getUser().getPhone() : null,
            v.getCollegeId(),
            v.getDepartment(),
            v.getYearOfStudy(),
            v.getJoinDate(),
            v.getStatus(),
            unitId,
            unitName,
            v.getCreatedAt()
        );
    }
}
