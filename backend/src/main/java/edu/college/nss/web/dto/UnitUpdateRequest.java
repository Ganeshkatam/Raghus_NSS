package edu.college.nss.web.dto;

public record UnitUpdateRequest(
    String unitName,
    Long officerId
) {}
