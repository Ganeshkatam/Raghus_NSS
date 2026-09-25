package edu.college.nss.web;

import edu.college.nss.service.NssUnitService;
import edu.college.nss.web.dto.MembershipRequest;
import edu.college.nss.web.dto.MembershipResponse;
import edu.college.nss.web.dto.UnitRequest;
import edu.college.nss.web.dto.UnitResponse;
import edu.college.nss.web.dto.UnitUpdateRequest;
import edu.college.nss.web.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/units")
public class NssUnitController {

    private final NssUnitService unitService;

    public NssUnitController(NssUnitService unitService) {
        this.unitService = unitService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR')")
    public ResponseEntity<UnitResponse> createUnit(@Valid @RequestBody UnitRequest request) {
        UnitResponse response = unitService.createUnit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UnitResponse>> listUnits() {
        List<UnitResponse> response = unitService.getAllUnits();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/officer-candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR')")
    public ResponseEntity<List<UserDto>> getOfficerCandidates() {
        List<UserDto> response = unitService.getEligibleOfficers();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnitResponse> getUnit(@PathVariable UUID id) {
        UnitResponse response = unitService.getUnitById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('UNITS_MANAGE') or hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<UnitResponse> updateUnit(
        @PathVariable UUID id,
        @Valid @RequestBody UnitUpdateRequest request,
        @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal
    ) {
        UnitResponse response = unitService.updateUnit(id, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('UNITS_MANAGE') or hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<MembershipResponse> addMember(
        @PathVariable UUID id,
        @Valid @RequestBody MembershipRequest request,
        @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal
    ) {
        MembershipResponse response = unitService.addMemberToUnit(id, request.volunteerId(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MembershipResponse>> getMembers(
        @PathVariable UUID id,
        @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal
    ) {
        List<MembershipResponse> responses = unitService.getUnitMembers(id, principal);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}/members/{membershipId}")
    @PreAuthorize("hasAuthority('UNITS_MANAGE') or hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<MembershipResponse> deactivateMember(
        @PathVariable UUID id,
        @PathVariable UUID membershipId,
        @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal
    ) {
        MembershipResponse response = unitService.deactivateMembership(id, membershipId, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('UNITS_MANAGE') or hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<MembershipResponse> transferVolunteer(
        @PathVariable UUID id,
        @Valid @RequestBody edu.college.nss.web.dto.UnitTransferRequest request,
        @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal
    ) {
        MembershipResponse response = unitService.transferVolunteer(id, request, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<edu.college.nss.web.dto.UnitStatsResponse> getUnitStats(@PathVariable UUID id) {
        return ResponseEntity.ok(unitService.getUnitStats(id));
    }

    @GetMapping("/volunteers/{volunteerId}/transfers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<edu.college.nss.web.dto.UnitTransferHistoryResponse>> getVolunteerTransferHistory(
        @PathVariable UUID volunteerId,
        @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal
    ) {
        return ResponseEntity.ok(unitService.getVolunteerTransferHistory(volunteerId, principal));
    }
}
