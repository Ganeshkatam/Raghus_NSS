package edu.college.nss.web.dto;

import jakarta.validation.constraints.NotNull;

public record CorrectionReviewRequest(
    @NotNull(message = "Approval status is required")
    Boolean approved,
    String remarks
) {}
