package edu.college.nss.web.dto;

import java.util.UUID;

public record EventUnitResponse(
    UUID unitId,
    String unitName,
    String unitNumber
) {}
