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
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final NssUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final VolunteerRepository volunteerRepository;
    private final UnitMembershipRepository membershipRepository;
    private final NotificationService notificationService;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public EventService(EventRepository eventRepository, EventRegistrationRepository registrationRepository,
                        NssUnitRepository unitRepository, UserRepository userRepository,
                        VolunteerRepository volunteerRepository, UnitMembershipRepository membershipRepository,
                        NotificationService notificationService, AttendanceRecordRepository attendanceRecordRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.volunteerRepository = volunteerRepository;
        this.membershipRepository = membershipRepository;
        this.notificationService = notificationService;
        this.attendanceRecordRepository = attendanceRecordRepository;
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
    public Page<EventResponse> search(UUID unitId, String status, Pageable pageable, UserDetails principal) {
        if (!isManager(principal)) {
            if ("DRAFT".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                return Page.empty(pageable);
            }
            return eventRepository.searchPublic(unitId, status, pageable).map(this::toResponse);
        }
        return eventRepository.search(unitId, status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID eventId) {
        return toResponse(getEntity(eventId));
    }

    @Transactional
    public EventResponse update(UUID eventId, EventUpdateRequest req, UserDetails principal) {
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
    public EventResponse transition(UUID eventId, String transition, UserDetails principal) {
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
    public EventRegistrationResponse register(UUID eventId, RegistrationRequest req, UserDetails principal) {
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
            if (!Boolean.TRUE.equals(req.joinWaitlist())) {
                throw new EventCapacityReachedException();
            }
            long waitlistCount = registrationRepository.countByEvent_EventIdAndStatus(eventId, "WAITLISTED");
            int position = (int) waitlistCount + 1;
            EventRegistration waitlisted = existing != null ? existing : new EventRegistration(event, volunteer);
            waitlisted.setStatus("WAITLISTED");
            waitlisted.setWaitlistPosition(position);
            waitlisted.setRegisteredAt(now);
            waitlisted = registrationRepository.save(waitlisted);

            if (volunteer.getUser() != null) {
                notificationService.sendNotification(
                    volunteer.getUser(),
                    "Joined Event Waitlist",
                    "Event capacity is full. You are on the waitlist at position #" + position + " for " + event.getTitle(),
                    "EVENT",
                    "/events/" + eventId
                );
            }
            return EventRegistrationResponse.fromEntity(waitlisted);
        }

        if (existing != null) {
            existing.setStatus("CONFIRMED");
            existing.setWaitlistPosition(null);
            existing.setRegisteredAt(now);
            existing = registrationRepository.save(existing);
            return EventRegistrationResponse.fromEntity(existing);
        }

        EventRegistration confirmed = new EventRegistration(event, volunteer, "CONFIRMED", null);
        confirmed = registrationRepository.save(confirmed);

        if (volunteer.getUser() != null) {
            notificationService.sendNotification(
                volunteer.getUser(),
                "Event Registration Confirmed",
                "Your registration for " + event.getTitle() + " has been confirmed.",
                "EVENT",
                "/events/" + eventId
            );
        }

        return EventRegistrationResponse.fromEntity(confirmed);
    }

    @Transactional(readOnly = true)
    public List<EventRegistrationResponse> registrations(UUID eventId, UserDetails principal) {
        Event event = getEntity(eventId);
        assertManagerForUnit(principal, event.getUnit());
        return registrationRepository.findByEvent_EventIdOrderByRegisteredAtAsc(eventId)
            .stream().map(EventRegistrationResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public EventRegistrationResponse myRegistration(UUID eventId, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Authenticated user is not registered as a volunteer."));
        EventRegistration registration = registrationRepository
            .findByEvent_EventIdAndVolunteer_VolunteerId(eventId, volunteer.getVolunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Registration not found."));
        return EventRegistrationResponse.fromEntity(registration);
    }

    @Transactional
    public void cancelRegistration(UUID eventId, UserDetails principal) {
        Event event = getEntity(eventId);
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Only a registered volunteer can cancel their registration."));
        EventRegistration registration = registrationRepository
            .findByEvent_EventIdAndVolunteer_VolunteerId(eventId, volunteer.getVolunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Registration not found."));
        if (!"REGISTERED".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus()) && !"WAITLISTED".equals(registration.getStatus())) {
            throw new IllegalArgumentException("Registration is already cancelled.");
        }
        if (!"OPEN".equals(event.getStatus())) {
            throw new RegistrationClosedException("Registration can only be cancelled while registration is open.");
        }

        boolean wasConfirmed = "REGISTERED".equals(registration.getStatus()) || "CONFIRMED".equals(registration.getStatus());
        registration.setStatus("CANCELLED");
        registration.setCancellationReason("Cancelled by volunteer");
        registration.setWaitlistPosition(null);
        registrationRepository.save(registration);

        if (wasConfirmed) {
            // Automatically promote the top waitlisted volunteer
            registrationRepository.findFirstByEvent_EventIdAndStatusOrderByWaitlistPositionAsc(eventId, "WAITLISTED")
                .ifPresent(promoted -> {
                    promoted.setStatus("CONFIRMED");
                    promoted.setWaitlistPosition(null);
                    registrationRepository.save(promoted);

                    // Re-index remaining waitlisted volunteers
                    List<EventRegistration> remainingWaitlist = registrationRepository
                        .findByEvent_EventIdAndStatusOrderByWaitlistPositionAsc(eventId, "WAITLISTED");
                    for (int i = 0; i < remainingWaitlist.size(); i++) {
                        remainingWaitlist.get(i).setWaitlistPosition(i + 1);
                    }
                    registrationRepository.saveAll(remainingWaitlist);

                    if (promoted.getVolunteer().getUser() != null) {
                        notificationService.sendNotification(
                            promoted.getVolunteer().getUser(),
                            "Promoted from Waitlist!",
                            "A spot has opened up for " + event.getTitle() + "! Your registration is now CONFIRMED.",
                            "EVENT",
                            "/events/" + eventId
                        );
                    }
                });
        }
    }

    @Transactional
    public EventResponse cloneEvent(UUID eventId, UserDetails principal) {
        Event source = getEntity(eventId);
        assertManagerForUnit(principal, source.getUnit());
        User creator = currentUser(principal);

        Instant newStart = source.getStartAt().plus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant newEnd = source.getEndAt().plus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant newOpen = source.getRegistrationOpenAt() != null ? source.getRegistrationOpenAt().plus(7, java.time.temporal.ChronoUnit.DAYS) : null;
        Instant newClose = source.getRegistrationCloseAt() != null ? source.getRegistrationCloseAt().plus(7, java.time.temporal.ChronoUnit.DAYS) : null;

        Event clone = new Event(
            source.getUnit(),
            creator,
            "Copy of " + source.getTitle(),
            source.getDescription(),
            source.getEventType(),
            newStart,
            newEnd,
            newOpen,
            newClose,
            source.getVenue(),
            source.getCapacity()
        );
        clone = eventRepository.save(clone);
        return toResponse(clone);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getEventStats(UUID eventId) {
        Event event = getEntity(eventId);
        long registeredCount = registrationRepository.countRegistered(eventId);
        long waitlistCount = registrationRepository.countByEvent_EventIdAndStatus(eventId, "WAITLISTED");
        long presentCount = attendanceRecordRepository.countByEventIdAndStatus(eventId, "PRESENT");
        long absentCount = attendanceRecordRepository.countByEventIdAndStatus(eventId, "ABSENT");

        double totalAttendance = presentCount + absentCount;
        double rate = totalAttendance > 0 ? (presentCount * 100.0 / totalAttendance) : 0.0;

        return new EventStatsResponse(
            event.getEventId(),
            event.getTitle(),
            event.getCapacity(),
            registeredCount,
            waitlistCount,
            presentCount,
            absentCount,
            rate
        );
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
        if (hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR")) return;
        if (hasRole(principal, "PROGRAMME_OFFICER")) {
            User user = currentUser(principal);
            if (unit.getOfficer() != null && unit.getOfficer().getUserId().equals(user.getUserId())) return;
        }
        throw new AccessDeniedException("You do not have permission to manage events for this NSS unit.");
    }

    private boolean isManager(UserDetails principal) {
        return hasRole(principal, "ADMIN")
            || hasRole(principal, "FACULTY_COORDINATOR")
            || hasRole(principal, "PROGRAMME_OFFICER");
    }

    private boolean hasRole(UserDetails principal, String role) {
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        String prefixedRole = "ROLE_" + cleanRole;
        return principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(cleanRole) || a.getAuthority().equals(prefixedRole));
    }

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    private Event getEntity(UUID eventId) {
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
