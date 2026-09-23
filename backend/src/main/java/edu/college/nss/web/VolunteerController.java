package edu.college.nss.web;

import edu.college.nss.service.VolunteerService;
import edu.college.nss.web.dto.MembershipResponse;
import edu.college.nss.web.dto.VolunteerRequest;
import edu.college.nss.web.dto.VolunteerResponse;
import edu.college.nss.web.dto.VolunteerUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/volunteers")
public class VolunteerController {

    private final VolunteerService volunteerService;

    public VolunteerController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<VolunteerResponse> createVolunteer(@Valid @RequestBody VolunteerRequest request) {
        VolunteerResponse response = volunteerService.createVolunteer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<Page<VolunteerResponse>> listVolunteers(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String department,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "15") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<VolunteerResponse> results = volunteerService.searchVolunteers(search, status, department, pageRequest);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VolunteerResponse> getCurrentVolunteer(
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(volunteerService.getCurrentVolunteer(principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VolunteerResponse> getVolunteer(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails principal
    ) {
        VolunteerResponse response = volunteerService.getVolunteerById(id, principal);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VolunteerResponse> updateVolunteer(
        @PathVariable Long id,
        @Valid @RequestBody VolunteerUpdateRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        VolunteerResponse response = volunteerService.updateVolunteer(id, request, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/memberships")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MembershipResponse>> getVolunteerMemberships(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails principal
    ) {
        List<MembershipResponse> responses = volunteerService.getVolunteerMemberships(id, principal);
        return ResponseEntity.ok(responses);
    }
}
