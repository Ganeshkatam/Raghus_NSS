package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.ServiceHourDTOs.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceHourService {

    private final ServiceHourEntryRepository serviceHourRepository;
    private final VolunteerRepository volunteerRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UnitMembershipRepository membershipRepository;
    private final edu.college.nss.security.UnitSecurityService unitSecurity;
    private final AuditService auditService;

    public ServiceHourService(ServiceHourEntryRepository serviceHourRepository,
                              VolunteerRepository volunteerRepository,
                              EventRepository eventRepository,
                              UserRepository userRepository,
                              NotificationService notificationService,
                              UnitMembershipRepository membershipRepository,
                              edu.college.nss.security.UnitSecurityService unitSecurity,
                              AuditService auditService) {
        this.serviceHourRepository = serviceHourRepository;
        this.volunteerRepository = volunteerRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.membershipRepository = membershipRepository;
        this.unitSecurity = unitSecurity;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public VolunteerServiceHoursSummary getMyServiceHours(UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername()).orElse(null);
        if (volunteer == null) {
            return null;
        }

        BigDecimal total = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        BigDecimal totalApproved = total != null ? total : BigDecimal.ZERO;

        BigDecimal regular = serviceHourRepository.sumApprovedHoursForVolunteerAndCategory(volunteer.getVolunteerId(), "REGULAR_ACTIVITY");
        BigDecimal community = serviceHourRepository.sumApprovedHoursForVolunteerAndCategory(volunteer.getVolunteerId(), "COMMUNITY_OUTREACH");
        BigDecimal blood = serviceHourRepository.sumApprovedHoursForVolunteerAndCategory(volunteer.getVolunteerId(), "BLOOD_DONATION");
        BigDecimal special = serviceHourRepository.sumApprovedHoursForVolunteerAndCategory(volunteer.getVolunteerId(), "SPECIAL_PROJECT");

        double regularHours = regular != null ? regular.doubleValue() : 0.0;
        double communityHours = community != null ? community.doubleValue() : 0.0;
        double otherHours = (blood != null ? blood.doubleValue() : 0.0) + (special != null ? special.doubleValue() : 0.0);

        double progressPercentage = Math.min(100.0, (totalApproved.doubleValue() / 120.0) * 100.0);
        progressPercentage = BigDecimal.valueOf(progressPercentage).setScale(1, RoundingMode.HALF_UP).doubleValue();

        List<ServiceHourEntry> list = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteer.getVolunteerId());

        long approvedCount = list.stream().filter(e -> "APPROVED".equals(e.getStatus())).count();
        long pendingCount = list.stream().filter(e -> "PENDING".equals(e.getStatus())).count();

        List<ServiceHourResponse> dtos = list.stream().map(this::toResponse).toList();

        return new VolunteerServiceHoursSummary(
            volunteer.getVolunteerId(),
            volunteer.getUser().getName(),
            volunteer.getCollegeId(),
            totalApproved,
            progressPercentage,
            regularHours,
            communityHours,
            otherHours,
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

        if (req.hours() == null || req.hours().compareTo(BigDecimal.ZERO) <= 0 || req.hours().compareTo(BigDecimal.valueOf(60)) > 0) {
            throw new IllegalArgumentException("Claimed hours must be strictly positive and cannot exceed 60 hours per submission.");
        }

        if (req.activityDate() != null && req.activityDate().isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Activity date cannot be in the future.");
        }

        Event event = null;
        if (req.eventId() != null) {
            event = eventRepository.findById(req.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + req.eventId()));

            if (event.getStartAt().isAfter(java.time.Instant.now())) {
                throw new IllegalArgumentException("Cannot claim service hours for an event that has not taken place yet.");
            }

            UnitMembership activeMembership = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId())
                .orElseThrow(() -> new AccessDeniedException("Volunteer must be enrolled in an active unit to claim event hours."));

            if (!activeMembership.getUnit().getUnitId().equals(event.getUnit().getUnitId())) {
                throw new AccessDeniedException("You can only claim hours for events organized by your assigned NSS Unit (" + activeMembership.getUnit().getUnitNumber() + ").");
            }

            boolean duplicate = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteer.getVolunteerId())
                .stream()
                .anyMatch(e -> e.getEvent() != null && e.getEvent().getEventId().equals(req.eventId()) &&
                               ("PENDING".equals(e.getStatus()) || "APPROVED".equals(e.getStatus())));
            if (duplicate) {
                throw new IllegalStateException("An active or approved service hour claim already exists for this event.");
            }
        }

        String cat = (req.category() != null && !req.category().isBlank()) ? req.category().trim().toUpperCase() : "REGULAR_ACTIVITY";

        ServiceHourEntry entry = new ServiceHourEntry(
            volunteer,
            event,
            null,
            req.hours(),
            "PENDING",
            null,
            req.description(),
            cat,
            req.evidenceNote(),
            req.activityDate()
        );

        ServiceHourEntry saved = serviceHourRepository.save(entry);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceHourResponse> getPendingClaims(UserDetails principal) {
        assertOfficerOrAdmin(principal);

        List<ServiceHourEntry> pending = serviceHourRepository.findByStatusOrderByCreatedAtDesc("PENDING");
        if (!unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedUnitIds = unitSecurity.getManagedUnitIds(principal);
            pending = pending.stream().filter(entry -> {
                UnitMembership mem = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(entry.getVolunteer().getVolunteerId()).orElse(null);
                return mem != null && mem.getUnit() != null && managedUnitIds.contains(mem.getUnit().getUnitId());
            }).toList();
        }

        return pending.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ServiceHourResponse reviewClaim(UUID entryId, ServiceHourReviewRequest req, UserDetails principal) {
        assertOfficerOrAdmin(principal);

        ServiceHourEntry entry = serviceHourRepository.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Service hour entry not found: " + entryId));

        if (!"PENDING".equals(entry.getStatus())) {
            throw new IllegalStateException("Only PENDING claims can be reviewed.");
        }

        if (!unitSecurity.isGlobalManager(principal)) {
            UnitMembership mem = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(entry.getVolunteer().getVolunteerId()).orElse(null);
            if (mem == null || !unitSecurity.canManageUnit(principal, mem.getUnit().getUnitId())) {
                throw new AccessDeniedException("You are not authorized to review claims for volunteers outside your assigned unit.");
            }
        }

        String action = req.action().trim().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new IllegalArgumentException("Action must be either APPROVE or REJECT.");
        }

        User reviewer = currentUser(principal);
        entry.setApprovedBy(reviewer);

        if ("APPROVE".equals(action)) {
            entry.setStatus("APPROVED");
            notificationService.sendNotification(
                entry.getVolunteer().getUser(),
                "Service Hour Claim Approved",
                "Your claim for " + entry.getHours() + " hours (" + entry.getCategory() + ") was approved by " + reviewer.getName() + ".",
                "SERVICE_HOURS",
                "/service-hours"
            );
        } else {
            entry.setStatus("REJECTED");
            if (req.reason() != null && !req.reason().trim().isBlank()) {
                String existing = entry.getDescription() != null ? entry.getDescription() + " " : "";
                entry.setDescription(existing + "[Reason: " + req.reason().trim() + "]");
            }
            notificationService.sendNotification(
                entry.getVolunteer().getUser(),
                "Service Hour Claim Rejected",
                "Your claim for " + entry.getHours() + " hours was rejected. " + (req.reason() != null ? "Reason: " + req.reason().trim() : ""),
                "SERVICE_HOURS",
                "/service-hours"
            );
        }

        ServiceHourEntry saved = serviceHourRepository.save(entry);

        auditService.logEvent(
            principal.getUsername(),
            "SERVICE_HOUR_CLAIM_" + entry.getStatus(),
            "SERVICE_HOUR_ENTRY",
            entry.getEntryId().toString(),
            entry.getVolunteer().getCollegeId() + " (" + entry.getHours() + " hrs)",
            "PENDING",
            entry.getStatus(),
            req.reason() != null ? req.reason() : "Service hour claim " + entry.getStatus()
        );

        return toResponse(saved);
    }

    private void assertOfficerOrAdmin(UserDetails principal) {
        boolean authorized = principal.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ADMIN") || a.getAuthority().equals("ROLE_ADMIN") ||
            a.getAuthority().equals("FACULTY_COORDINATOR") || a.getAuthority().equals("ROLE_FACULTY_COORDINATOR") ||
            a.getAuthority().equals("PROGRAMME_OFFICER") || a.getAuthority().equals("ROLE_PROGRAMME_OFFICER")
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
            e.getCategory(),
            e.getEvidenceNote(),
            e.getActivityDate(),
            e.getCreatedAt()
        );
    }
}
