package edu.college.nss.web;

import edu.college.nss.service.AttendanceService;
import edu.college.nss.web.dto.AttendanceDTOs.*;
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
@RequestMapping("/api/v1")
public class AttendanceController {
    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @PostMapping("/events/{eventId}/attendance/sessions")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
    public ResponseEntity<SessionResponse> openSession(@PathVariable UUID eventId,
                                                       @Valid @RequestBody CreateSessionRequest request,
                                                       @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.openSession(eventId, request, principal));
    }

    @GetMapping("/events/{eventId}/attendance/sessions/active")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
    public ResponseEntity<SessionResponse> getActiveSession(@PathVariable UUID eventId,
                                                            @AuthenticationPrincipal UserDetails principal) {
        SessionResponse response = service.getActiveSession(eventId, principal);
        if (response == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attendance/sessions/{sessionId}/close")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
    public ResponseEntity<SessionResponse> closeSession(@PathVariable UUID sessionId,
                                                        @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.closeSession(sessionId, principal));
    }

    @PostMapping("/attendance/check-in")
    @PreAuthorize("hasAuthority('ATTENDANCE_CHECKIN') or hasAnyRole('VOLUNTEER', 'STUDENT_LEADER') or isAuthenticated()")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request,
                                                   @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.checkInWithQr(request, principal));
    }

    @PostMapping("/attendance/sessions/{sessionId}/manual")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
    public ResponseEntity<CheckInResponse> manualCheckIn(@PathVariable UUID sessionId,
                                                         @Valid @RequestBody ManualCheckInRequest request,
                                                         @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.manualCheckIn(sessionId, request, principal));
    }

    @GetMapping("/events/{eventId}/attendance/roster")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
    public ResponseEntity<List<AttendanceRosterItem>> getRoster(@PathVariable UUID eventId,
                                                               @RequestParam(required = false) UUID sessionId,
                                                               @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getRoster(eventId, sessionId, principal));
    }

    @PostMapping("/attendance/records/{attendanceId}/correct")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
    public ResponseEntity<CorrectionResponse> correctAttendance(@PathVariable UUID attendanceId,
                                                                @Valid @RequestBody CorrectionRequest request,
                                                                @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.correctAttendance(attendanceId, request, principal));
    }

    @GetMapping("/attendance/corrections/pending")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<List<CorrectionResponse>> getPendingCorrections(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getPendingCorrections(principal));
    }

    @PostMapping("/attendance/corrections/{id}/review")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<CorrectionResponse> reviewCorrection(@PathVariable UUID id,
                                                                @Valid @RequestBody edu.college.nss.web.dto.CorrectionReviewRequest request,
                                                                @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.reviewCorrection(id, request, principal));
    }
}
