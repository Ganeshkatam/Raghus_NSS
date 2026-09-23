package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.AnnouncementDTOs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AnnouncementIntegrationTest {

    @Autowired AnnouncementService announcementService;
    @Autowired AnnouncementRepository announcementRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ServiceHourEntryRepository serviceHourRepository;
    @Autowired AttendanceCorrectionRepository correctionRepository;
    @Autowired AttendanceRecordRepository recordRepository;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired EventRegistrationRepository registrationRepository;
    @Autowired EventRepository eventRepository;
    @Autowired UnitMembershipRepository membershipRepository;
    @Autowired VolunteerRepository volunteerRepository;
    @Autowired NssUnitRepository unitRepository;
    @Autowired UserRepository userRepository;

    private User admin;
    private UserDetails adminPrincipal;
    private NssUnit unit;
    private Volunteer volunteer;
    private UserDetails volunteerPrincipal;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        announcementRepository.deleteAll();
        serviceHourRepository.deleteAll();
        correctionRepository.deleteAll();
        recordRepository.deleteAll();
        sessionRepository.deleteAll();
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        membershipRepository.deleteAll();
        volunteerRepository.deleteAll();
        unitRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Admin User", "admin@raghunss.edu", "pass", null));
        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        unit = unitRepository.save(new NssUnit("Unit 1", "REC-01", admin));

        User volUser = userRepository.save(new User("Kavya Sri", "kavya@raghunss.edu", "pass", null));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "21B91A0503", "ECE", 2));
        membershipRepository.save(new UnitMembership(volunteer, unit));

        volunteerPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(volUser.getEmail()).password("pass").roles("VOLUNTEER").build();
    }

    @Test
    void createAnnouncement_collegeWide_shouldBroadcastNotification() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(
            "Independence Day Parade",
            "All units to gather at the central ground by 7:30 AM.",
            null,
            null
        );

        AnnouncementResponse response = announcementService.createAnnouncement(req, adminPrincipal);

        assertNotNull(response.announcementId());
        assertEquals("Independence Day Parade", response.title());
        assertEquals("College-Wide", response.unitName());

        List<NotificationResponse> volNotifs = announcementService.getMyNotifications(volunteerPrincipal);
        assertFalse(volNotifs.isEmpty());
        assertTrue(volNotifs.get(0).title().contains("Independence Day Parade"));
        assertFalse(volNotifs.get(0).isRead());
    }

    @Test
    void createAnnouncement_unitSpecific_shouldBroadcastToUnitMembers() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(
            "Unit 1 Special Briefing",
            "Urgent briefing for village camp logistics.",
            unit.getUnitId(),
            null
        );

        AnnouncementResponse response = announcementService.createAnnouncement(req, adminPrincipal);

        assertEquals("Unit 1", response.unitName());
        List<NotificationResponse> volNotifs = announcementService.getMyNotifications(volunteerPrincipal);
        assertEquals(1, volNotifs.size());
        assertTrue(volNotifs.get(0).title().contains("[Unit 1]"));
    }

    @Test
    void markNotificationRead_shouldUpdateStatus() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(
            "Blood Donation Notice",
            "Donor registration open.",
            unit.getUnitId(),
            null
        );
        announcementService.createAnnouncement(req, adminPrincipal);

        List<NotificationResponse> notifs = announcementService.getMyNotifications(volunteerPrincipal);
        Long notifId = notifs.get(0).notificationId();

        announcementService.markNotificationRead(notifId, volunteerPrincipal);

        List<NotificationResponse> updated = announcementService.getMyNotifications(volunteerPrincipal);
        assertTrue(updated.get(0).isRead());
    }

    @Test
    void createAnnouncement_asVolunteer_shouldBeDenied() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(
            "Unauthorized Post", "Testing RBAC violation", null, null
        );

        assertThrows(AccessDeniedException.class,
            () -> announcementService.createAnnouncement(req, volunteerPrincipal));
    }
}
