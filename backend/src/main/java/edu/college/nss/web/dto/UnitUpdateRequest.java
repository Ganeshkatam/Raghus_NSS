package edu.college.nss.web.dto;

import java.util.UUID;

public record UnitUpdateRequest(
    String unitName,
    UUID officerId,
    Boolean clearOfficer
) {
    public UnitUpdateRequest(String unitName, UUID officerId) {
        this(unitName, officerId, false);
    }
}

