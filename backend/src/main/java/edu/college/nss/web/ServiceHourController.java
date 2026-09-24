package edu.college.nss.web;

import edu.college.nss.service.ServiceHourService;
import edu.college.nss.web.dto.ServiceHourDTOs.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-hours")
public class ServiceHourController {

    private final ServiceHourService service;

    public ServiceHourController(ServiceHourService service) {
        this.service = service;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VolunteerServiceHoursSummary> getMyServiceHours(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getMyServiceHours(principal));
    }

    @PostMapping("/claim")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<ServiceHourResponse> submitClaim(@Valid @RequestBody ServiceHourClaimRequest request,
                                                           @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitClaim(request, principal));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<List<ServiceHourResponse>> getPendingClaims(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getPendingClaims(principal));
    }

    @PostMapping("/{entryId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<ServiceHourResponse> reviewClaim(@PathVariable UUID entryId,
                                                           @Valid @RequestBody ServiceHourReviewRequest request,
                                                           @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.reviewClaim(entryId, request, principal));
    }
}
