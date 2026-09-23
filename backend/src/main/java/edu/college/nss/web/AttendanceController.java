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

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {
    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @PostMapping("/events/{eventId}/attendance/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<SessionResponse> openSession(@PathVariable Long eventId,
                                                       @Valid @RequestBody CreateSessionRequest request,
                                                       @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.openSession(eventId, request, principal));
    }

    @GetMapping("/events/{eventId}/attendance/sessions/active")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<SessionResponse> getActiveSession(@PathVariable Long eventId,
                                                            @AuthenticationPrincipal UserDetails principal) {
        SessionResponse response = service.getActiveSession(eventId, principal);
        if (response == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attendance/sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<SessionResponse> closeSession(@PathVariable Long sessionId,
                                                        @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.closeSession(sessionId, principal));
    }

    @PostMapping("/attendance/check-in")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request,
                                                   @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.checkInWithQr(request, principal));
    }

    @PostMapping("/attendance/sessions/{sessionId}/manual")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<CheckInResponse> manualCheckIn(@PathVariable Long sessionId,
                                                         @Valid @RequestBody ManualCheckInRequest request,
                                                         @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.manualCheckIn(sessionId, request, principal));
    }

    @GetMapping("/events/{eventId}/attendance/roster")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<List<AttendanceRosterItem>> getRoster(@PathVariable Long eventId,
                                                               @RequestParam(required = false) Long sessionId,
                                                               @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getRoster(eventId, sessionId, principal));
    }

    @PostMapping("/attendance/records/{attendanceId}/correct")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<CorrectionResponse> correctAttendance(@PathVariable Long attendanceId,
                                                                @Valid @RequestBody CorrectionRequest request,
                                                                @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.correctAttendance(attendanceId, request, principal));
    }
}
