package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.ServiceHourDTOs.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ServiceHourService {

    private final ServiceHourEntryRepository serviceHourRepository;
    private final VolunteerRepository volunteerRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public ServiceHourService(ServiceHourEntryRepository serviceHourRepository,
                              VolunteerRepository volunteerRepository,
                              EventRepository eventRepository,
                              UserRepository userRepository) {
        this.serviceHourRepository = serviceHourRepository;
        this.volunteerRepository = volunteerRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public VolunteerServiceHoursSummary getMyServiceHours(UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername()).orElse(null);
        if (volunteer == null) {
            return null;
        }

        BigDecimal total = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        List<ServiceHourEntry> list = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteer.getVolunteerId());

        long approvedCount = list.stream().filter(e -> "APPROVED".equals(e.getStatus())).count();
        long pendingCount = list.stream().filter(e -> "PENDING".equals(e.getStatus())).count();

        List<ServiceHourResponse> dtos = list.stream().map(this::toResponse).toList();

        return new VolunteerServiceHoursSummary(
            volunteer.getVolunteerId(),
            volunteer.getUser().getName(),
            volunteer.getCollegeId(),
            total != null ? total : BigDecimal.ZERO,
            approvedCount,
            pendingCount,
            dtos
        );
    }

    @Transactional
    public ServiceHourResponse submitClaim(ServiceHourClaimRequest req, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername())
            .orElseThrow(() -> new AccessDeniedException("Only active volunteers can submit service hour claims."));

        if (!"ACTIVE".equalsIgnoreCase(volunteer.getStatus())) {
            throw new AccessDeniedException("Volunteer status must be ACTIVE to claim service hours.");
        }

        Event event = null;
        if (req.eventId() != null) {
            event = eventRepository.findById(req.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + req.eventId()));
        }

        ServiceHourEntry entry = new ServiceHourEntry(
            volunteer,
            event,
            null,
            req.hours(),
            "PENDING",
            null,
            req.description()
        );

        ServiceHourEntry saved = serviceHourRepository.save(entry);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceHourResponse> getPendingClaims(UserDetails principal) {
        assertOfficerOrAdmin(principal);

        List<ServiceHourEntry> pending = serviceHourRepository.findByStatusOrderByCreatedAtDesc("PENDING");
        return pending.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ServiceHourResponse reviewClaim(Long entryId, ServiceHourReviewRequest req, UserDetails principal) {
        assertOfficerOrAdmin(principal);

        ServiceHourEntry entry = serviceHourRepository.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Service hour entry not found: " + entryId));

        if (!"PENDING".equals(entry.getStatus())) {
            throw new IllegalStateException("Only PENDING claims can be reviewed.");
        }

        String action = req.action().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new IllegalArgumentException("Action must be either APPROVE or REJECT.");
        }

        User reviewer = currentUser(principal);
        entry.setApprovedBy(reviewer);

        if ("APPROVE".equals(action)) {
            entry.setStatus("APPROVED");
        } else {
            entry.setStatus("REJECTED");
            if (req.reason() != null && !req.reason().trim().isBlank()) {
                String existing = entry.getDescription() != null ? entry.getDescription() + " " : "";
                entry.setDescription(existing + "[Reason: " + req.reason().trim() + "]");
            }
        }

        ServiceHourEntry saved = serviceHourRepository.save(entry);
        return toResponse(saved);
    }

    private void assertOfficerOrAdmin(UserDetails principal) {
        boolean authorized = principal.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_ADMIN") ||
            a.getAuthority().equals("ROLE_FACULTY_COORDINATOR") ||
            a.getAuthority().equals("ROLE_PROGRAMME_OFFICER")
        );
        if (!authorized) {
            throw new AccessDeniedException("Access denied. Officer or Admin privileges required.");
        }
    }

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found: " + principal.getUsername()));
    }

    private ServiceHourResponse toResponse(ServiceHourEntry e) {
        return new ServiceHourResponse(
            e.getEntryId(),
            e.getVolunteer().getVolunteerId(),
            e.getVolunteer().getUser().getName(),
            e.getVolunteer().getCollegeId(),
            e.getEvent() != null ? e.getEvent().getEventId() : null,
            e.getEvent() != null ? e.getEvent().getTitle() : null,
            e.getHours(),
            e.getStatus(),
            e.getApprovedBy() != null ? e.getApprovedBy().getName() : null,
            e.getDescription(),
            e.getCreatedAt()
        );
    }
}
