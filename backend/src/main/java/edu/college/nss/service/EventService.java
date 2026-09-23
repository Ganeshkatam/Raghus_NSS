package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.exception.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final NssUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final VolunteerRepository volunteerRepository;
    private final UnitMembershipRepository membershipRepository;

    public EventService(EventRepository eventRepository, EventRegistrationRepository registrationRepository,
                        NssUnitRepository unitRepository, UserRepository userRepository,
                        VolunteerRepository volunteerRepository, UnitMembershipRepository membershipRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.volunteerRepository = volunteerRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public EventResponse create(EventCreateRequest req, UserDetails principal) {
        User creator = currentUser(principal);
        NssUnit unit = unitRepository.findById(req.unitId())
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + req.unitId()));
        assertManagerForUnit(principal, unit);
        validateTimes(req.startAt(), req.endAt(), req.registrationOpenAt(), req.registrationCloseAt());

        Event event = new Event(unit, creator, req.title().trim(), req.description(), req.eventType().trim(),
            req.startAt(), req.endAt(), req.registrationOpenAt(), req.registrationCloseAt(),
            req.venue().trim(), req.capacity());
        return toResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> search(Long unitId, String status, Pageable pageable) {
        return eventRepository.search(unitId, status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long eventId) {
        return toResponse(getEntity(eventId));
    }

    @Transactional
    public EventResponse update(Long eventId, EventUpdateRequest req, UserDetails principal) {
        Event e = getEntity(eventId);
        assertManagerForUnit(principal, e.getUnit());
        if (!"DRAFT".equals(e.getStatus())) {
            throw new IllegalArgumentException("Only draft events can be edited.");
        }

        Instant start = req.startAt() != null ? req.startAt() : e.getStartAt();
        Instant end = req.endAt() != null ? req.endAt() : e.getEndAt();
        Instant open = req.registrationOpenAt() != null ? req.registrationOpenAt() : e.getRegistrationOpenAt();
        Instant close = req.registrationCloseAt() != null ? req.registrationCloseAt() : e.getRegistrationCloseAt();
        validateTimes(start, end, open, close);

        if (req.title() != null && !req.title().isBlank()) e.setTitle(req.title().trim());
        if (req.description() != null) e.setDescription(req.description());
        if (req.eventType() != null && !req.eventType().isBlank()) e.setEventType(req.eventType().trim());
        if (req.startAt() != null) e.setStartAt(req.startAt());
        if (req.endAt() != null) e.setEndAt(req.endAt());
        if (req.registrationOpenAt() != null) e.setRegistrationOpenAt(req.registrationOpenAt());
        if (req.registrationCloseAt() != null) e.setRegistrationCloseAt(req.registrationCloseAt());
        if (req.venue() != null && !req.venue().isBlank()) e.setVenue(req.venue().trim());
        if (req.capacity() != null) {
            long registered = registrationRepository.countRegistered(eventId);
            if (req.capacity() < registered) {
                throw new IllegalArgumentException("Capacity cannot be lower than the current registered count.");
            }
            e.setCapacity(req.capacity());
        }
        return toResponse(eventRepository.save(e));
    }

    @Transactional
    public EventResponse transition(Long eventId, String transition, UserDetails principal) {
        Event e = getEntity(eventId);
        assertManagerForUnit(principal, e.getUnit());
        switch (transition) {
            case "publish" -> e.publish();
            case "open" -> e.open();
            case "close" -> e.close();
            case "cancel" -> e.cancel();
            case "complete" -> e.complete();
            default -> throw new IllegalArgumentException("Unsupported event transition.");
        }
        return toResponse(eventRepository.save(e));
    }

    @Transactional
    public EventRegistrationResponse register(Long eventId, RegistrationRequest req, UserDetails principal) {
        Event event = eventRepository.findByIdWithLock(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));

        Volunteer volunteer = volunteerRepository.findById(req.volunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + req.volunteerId()));

        assertCanRegister(principal, volunteer);
        if (!"OPEN".equals(event.getStatus())) {
            throw new RegistrationClosedException("Event registration is not open.");
        }

        Instant now = Instant.now();
        if (event.getRegistrationOpenAt() != null && now.isBefore(event.getRegistrationOpenAt())) {
            throw new RegistrationClosedException("Registration window has not opened yet.");
        }
        if (event.getRegistrationCloseAt() != null && !now.isBefore(event.getRegistrationCloseAt())) {
            throw new RegistrationClosedException("Registration window has closed.");
        }

        membershipRepository.findByVolunteer_VolunteerIdAndUnit_UnitIdAndIsActiveTrue(
                volunteer.getVolunteerId(), event.getUnit().getUnitId())
            .orElseThrow(() -> new AccessDeniedException("Volunteer is not an active member of this event's NSS unit."));

        EventRegistration existing = registrationRepository
            .findByEvent_EventIdAndVolunteer_VolunteerId(eventId, volunteer.getVolunteerId())
            .orElse(null);

        if (existing != null && !"CANCELLED".equals(existing.getStatus())) {
            throw new DuplicateRegistrationException();
        }

        long registered = registrationRepository.countRegistered(eventId);
        if (registered >= event.getCapacity()) {
            throw new EventCapacityReachedException();
        }

        if (existing != null) {
            existing.setStatus("REGISTERED");
            existing.setRegisteredAt(now);
            return EventRegistrationResponse.fromEntity(registrationRepository.save(existing));
        }

        return EventRegistrationResponse.fromEntity(
            registrationRepository.save(new EventRegistration(event, volunteer))
        );
    }

    @Transactional(readOnly = true)
    public List<EventRegistrationResponse> registrations(Long eventId, UserDetails principal) {
        Event event = getEntity(eventId);
        assertManagerForUnit(principal, event.getUnit());
        return registrationRepository.findByEvent_EventIdOrderByRegisteredAtAsc(eventId)
            .stream().map(EventRegistrationResponse::fromEntity).toList();
    }

    @Transactional
    public void cancelRegistration(Long eventId, UserDetails principal) {
        Event event = getEntity(eventId);
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Only a registered volunteer can cancel their registration."));
        EventRegistration registration = registrationRepository
            .findByEvent_EventIdAndVolunteer_VolunteerId(eventId, volunteer.getVolunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Registration not found."));
        if (!"REGISTERED".equals(registration.getStatus())) {
            throw new IllegalArgumentException("Registration is already cancelled.");
        }
        if (!"OPEN".equals(event.getStatus())) {
            throw new RegistrationClosedException("Registration can only be cancelled while registration is open.");
        }
        registration.setStatus("CANCELLED");
        registrationRepository.save(registration);
    }

    private void assertCanRegister(UserDetails principal, Volunteer volunteer) {
        if (!volunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername())) {
            throw new AccessDeniedException("You can only register yourself for an event.");
        }
        if (!"ACTIVE".equalsIgnoreCase(volunteer.getStatus()) || !"ACTIVE".equalsIgnoreCase(volunteer.getUser().getStatus())) {
            throw new AccessDeniedException("Only active volunteers may register.");
        }
    }

    private void assertManagerForUnit(UserDetails principal, NssUnit unit) {
        if (hasRole(principal, "ROLE_ADMIN") || hasRole(principal, "ROLE_FACULTY_COORDINATOR")) return;
        if (hasRole(principal, "ROLE_PROGRAMME_OFFICER")) {
            User user = currentUser(principal);
            if (unit.getOfficer() != null && unit.getOfficer().getUserId().equals(user.getUserId())) return;
        }
        throw new AccessDeniedException("You do not have permission to manage events for this NSS unit.");
    }

    private boolean hasRole(UserDetails principal, String role) {
        return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private Event getEntity(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));
    }

    private EventResponse toResponse(Event e) {
        return EventResponse.fromEntity(e, registrationRepository.countRegistered(e.getEventId()));
    }

    private void validateTimes(Instant start, Instant end, Instant open, Instant close) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Event end time must be after event start time.");
        }
        if (open != null && close != null && close.isBefore(open)) {
            throw new IllegalArgumentException("Registration close time must be on or after registration open time.");
        }
        if (close != null && close.isAfter(start)) {
            throw new IllegalArgumentException("Registration must close before the event starts.");
        }
        if (open != null && open.isAfter(start)) {
            throw new IllegalArgumentException("Registration cannot open after the event starts.");
        }
    }
}
