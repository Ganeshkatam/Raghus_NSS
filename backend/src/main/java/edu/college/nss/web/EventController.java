package edu.college.nss.web;

import edu.college.nss.service.EventService;
import edu.college.nss.web.dto.*;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService service;
    public EventController(EventService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventCreateRequest request,
                                                  @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<EventResponse> search(@RequestParam(required = false) UUID unitId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "12") int size,
                                      @AuthenticationPrincipal UserDetails principal) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return service.search(unitId, status, PageRequest.of(page, safeSize, Sort.by("startAt").ascending()), principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public EventResponse get(@PathVariable UUID id) { return service.get(id); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventUpdateRequest request,
                                @AuthenticationPrincipal UserDetails principal) {
        return service.update(id, request, principal);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse publish(@PathVariable UUID id, @AuthenticationPrincipal UserDetails p) { return service.transition(id, "publish", p); }
    @PostMapping("/{id}/open")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse open(@PathVariable UUID id, @AuthenticationPrincipal UserDetails p) { return service.transition(id, "open", p); }
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse close(@PathVariable UUID id, @AuthenticationPrincipal UserDetails p) { return service.transition(id, "close", p); }
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal UserDetails p) { return service.transition(id, "cancel", p); }
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse complete(@PathVariable UUID id, @AuthenticationPrincipal UserDetails p) { return service.transition(id, "complete", p); }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public EventResponse clone(@PathVariable UUID id, @AuthenticationPrincipal UserDetails p) { return service.cloneEvent(id, p); }

    @GetMapping("/{id}/stats")
    @PreAuthorize("isAuthenticated()")
    public edu.college.nss.web.dto.EventStatsResponse stats(@PathVariable UUID id) { return service.getEventStats(id); }

    @PostMapping("/{id}/registrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EventRegistrationResponse> register(@PathVariable UUID id,
                                                               @Valid @RequestBody RegistrationRequest request,
                                                               @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(id, request, principal));
    }

    @PostMapping("/{id}/waitlist")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EventRegistrationResponse> joinWaitlist(@PathVariable UUID id,
                                                                   @Valid @RequestBody RegistrationRequest request,
                                                                   @AuthenticationPrincipal UserDetails principal) {
        RegistrationRequest waitlistReq = new RegistrationRequest(request.volunteerId(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(id, waitlistReq, principal));
    }

    @GetMapping("/registrations/me")
    @PreAuthorize("isAuthenticated()")
    public List<EventRegistrationResponse> myRegistrations(@AuthenticationPrincipal UserDetails principal) {
        return service.myRegistrations(principal);
    }

    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAuthority('EVENTS_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
    public List<EventRegistrationResponse> registrations(@PathVariable UUID id,
                                                         @AuthenticationPrincipal UserDetails principal) {
        return service.registrations(id, principal);
    }

    @GetMapping("/{id}/registrations/me")
    @PreAuthorize("isAuthenticated()")
    public EventRegistrationResponse myRegistration(@PathVariable UUID id,
                                                    @AuthenticationPrincipal UserDetails principal) {
        return service.myRegistration(id, principal);
    }

    @DeleteMapping("/{id}/registrations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelRegistration(@PathVariable UUID id,
                                                    @Valid @RequestBody(required = false) CancelRegistrationRequest request,
                                                    @AuthenticationPrincipal UserDetails principal) {
        service.cancelRegistration(id, request != null ? request.reason() : null, principal);
        return ResponseEntity.noContent().build();
    }
}
