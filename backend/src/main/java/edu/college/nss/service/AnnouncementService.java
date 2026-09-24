package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.AnnouncementDTOs.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;
    private final NssUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final VolunteerRepository volunteerRepository;
    private final UnitMembershipRepository membershipRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               NotificationService notificationService,
                               NssUnitRepository unitRepository,
                               UserRepository userRepository,
                               VolunteerRepository volunteerRepository,
                               UnitMembershipRepository membershipRepository) {
        this.announcementRepository = announcementRepository;
        this.notificationService = notificationService;
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.volunteerRepository = volunteerRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(CreateAnnouncementRequest req, UserDetails principal) {
        assertCanAnnounce(principal);

        User author = currentUser(principal);
        NssUnit unit = null;
        if (req.unitId() != null) {
            unit = unitRepository.findById(req.unitId())
                .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + req.unitId()));
        }

        Announcement announcement = new Announcement(
            author,
            unit,
            req.title().trim(),
            req.content().trim(),
            Instant.now(),
            req.expiresAt()
        );
        if (req.priority() != null && !req.priority().isBlank()) {
            announcement.setPriority(req.priority().trim().toUpperCase());
        }
        Announcement saved = announcementRepository.save(announcement);

        // Broadcast notifications
        if (unit != null) {
            List<UnitMembership> members = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unit.getUnitId());
            for (UnitMembership m : members) {
                notificationService.sendNotification(
                    m.getVolunteer().getUser(),
                    "[" + unit.getUnitName() + "] " + saved.getTitle(),
                    saved.getContent(),
                    "ANNOUNCEMENT",
                    "/announcements"
                );
            }
        } else {
            // College-wide notification
            List<User> allUsers = userRepository.findAll();
            for (User u : allUsers) {
                notificationService.sendNotification(
                    u,
                    "[NSS Notice] " + saved.getTitle(),
                    saved.getContent(),
                    "ANNOUNCEMENT",
                    "/announcements"
                );
            }
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getAnnouncements(UserDetails principal) {
        if (principal == null) {
            return announcementRepository.findByUnitIsNullOrderByPublishedAtDesc()
                .stream().map(this::toResponse).toList();
        }

        if (hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR")) {
            return announcementRepository.findAllByOrderByPublishedAtDesc()
                .stream().map(this::toResponse).toList();
        }

        if (hasRole(principal, "PROGRAMME_OFFICER")) {
            User officer = currentUser(principal);
            List<NssUnit> officerUnits = unitRepository.findByOfficer_UserId(officer.getUserId());
            if (!officerUnits.isEmpty()) {
                return announcementRepository.findVisibleForUnit(officerUnits.get(0).getUnitId())
                    .stream().map(this::toResponse).toList();
            }
            return announcementRepository.findAllByOrderByPublishedAtDesc()
                .stream().map(this::toResponse).toList();
        }

        // Volunteer: check active membership
        return volunteerRepository.findByUser_Email(principal.getUsername())
            .flatMap(v -> membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(v.getVolunteerId()))
            .map(m -> announcementRepository.findVisibleForUnit(m.getUnit().getUnitId()))
            .orElseGet(announcementRepository::findAllByOrderByPublishedAtDesc)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteAnnouncement(UUID id, UserDetails principal) {
        Announcement announcement = announcementRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + id));

        User current = currentUser(principal);
        boolean isAdmin = hasRole(principal, "ADMIN") || hasRole(principal, "FACULTY_COORDINATOR");
        boolean isAuthor = announcement.getCreatedBy().getUserId().equals(current.getUserId());

        if (!isAdmin && !isAuthor) {
            throw new AccessDeniedException("You are not authorized to delete this announcement.");
        }

        announcementRepository.delete(announcement);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UserDetails principal) {
        return notificationService.getMyNotifications(principal);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserDetails principal) {
        return notificationService.getUnreadCount(principal);
    }

    @Transactional
    public void markNotificationRead(UUID id, UserDetails principal) {
        notificationService.markAsRead(id, principal);
    }

    @Transactional
    public void markAllNotificationsRead(UserDetails principal) {
        notificationService.markAllAsRead(principal);
    }

    private void assertCanAnnounce(UserDetails principal) {
        boolean can = hasRole(principal, "ADMIN") ||
                      hasRole(principal, "FACULTY_COORDINATOR") ||
                      hasRole(principal, "PROGRAMME_OFFICER");
        if (!can) {
            throw new AccessDeniedException("Only designated Officers and Administrators can publish announcements.");
        }
    }

    private boolean hasRole(UserDetails principal, String role) {
        String cleanRole = role.startsWith("ROLE_") ? role.substring(5) : role;
        String prefixedRole = "ROLE_" + cleanRole;
        return principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(cleanRole) || a.getAuthority().equals(prefixedRole));
    }

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found: " + principal.getUsername()));
    }

    private AnnouncementResponse toResponse(Announcement a) {
        return new AnnouncementResponse(
            a.getAnnouncementId(),
            a.getTitle(),
            a.getContent(),
            a.getUnit() != null ? a.getUnit().getUnitId() : null,
            a.getUnit() != null ? a.getUnit().getUnitName() : "College-Wide",
            a.getPriority() != null ? a.getPriority() : "NORMAL",
            a.getCreatedBy().getName(),
            a.getPublishedAt(),
            a.getExpiresAt()
        );
    }
}
