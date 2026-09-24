package edu.college.nss.web;

import edu.college.nss.service.AnnouncementService;
import edu.college.nss.web.dto.AnnouncementDTOs.*;
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
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<AnnouncementResponse> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request,
                                                                   @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAnnouncement(request, principal));
    }

    @GetMapping("/announcements")
    public ResponseEntity<List<AnnouncementResponse>> getAnnouncements(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getAnnouncements(principal));
    }

    @DeleteMapping("/announcements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable UUID id,
                                                   @AuthenticationPrincipal UserDetails principal) {
        service.deleteAnnouncement(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.getMyNotifications(principal));
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markNotificationRead(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserDetails principal) {
        service.markNotificationRead(id, principal);
        return ResponseEntity.ok().build();
    }
}
