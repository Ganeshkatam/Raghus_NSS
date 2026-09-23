package edu.college.nss.web;

import edu.college.nss.service.NssUnitService;
import edu.college.nss.web.dto.MembershipRequest;
import edu.college.nss.web.dto.MembershipResponse;
import edu.college.nss.web.dto.UnitRequest;
import edu.college.nss.web.dto.UnitResponse;
import edu.college.nss.web.dto.UnitUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnitResponse> getUnit(@PathVariable Long id) {
        UnitResponse response = unitService.getUnitById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR')")
    public ResponseEntity<UnitResponse> updateUnit(
        @PathVariable Long id,
        @Valid @RequestBody UnitUpdateRequest request
    ) {
        UnitResponse response = unitService.updateUnit(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<MembershipResponse> addMember(
        @PathVariable Long id,
        @Valid @RequestBody MembershipRequest request
    ) {
        MembershipResponse response = unitService.addMemberToUnit(id, request.volunteerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MembershipResponse>> getMembers(@PathVariable Long id) {
        List<MembershipResponse> responses = unitService.getUnitMembers(id);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}/members/{membershipId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
    public ResponseEntity<MembershipResponse> deactivateMember(
        @PathVariable Long id,
        @PathVariable Long membershipId
    ) {
        MembershipResponse response = unitService.deactivateMembership(id, membershipId);
        return ResponseEntity.ok(response);
    }
}
