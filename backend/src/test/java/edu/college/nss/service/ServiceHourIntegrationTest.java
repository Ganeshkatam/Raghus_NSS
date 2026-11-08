package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.ServiceHourDTOs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ServiceHourIntegrationTest {

    @Autowired ServiceHourService serviceHourService;
    @Autowired ServiceHourEntryRepository serviceHourRepository;
    @Autowired AttendanceCorrectionRepository correctionRepository;
    @Autowired AttendanceRecordRepository recordRepository;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired EventRegistrationRepository registrationRepository;
    @Autowired EventRepository eventRepository;
    @Autowired UnitMembershipRepository membershipRepository;
    @Autowired VolunteerRepository volunteerRepository;
    @Autowired VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired UnitTransferHistoryRepository transferHistoryRepository;
    @Autowired NssUnitRepository unitRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AnnouncementRepository announcementRepository;

    private User admin;
    private UserDetails adminPrincipal;
    private Volunteer volunteer;
    private UserDetails volunteerPrincipal;
    private NssUnit unit;

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
        statusHistoryRepository.deleteAll();
        transferHistoryRepository.deleteAll();
        volunteerRepository.deleteAll();
        unitRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Admin PO", "po@raghunss.edu", "pass", null));
        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));

        unit = unitRepository.save(new NssUnit("Unit 1", "REC-01", admin));

        User volUser = userRepository.save(new User("Kiran Kumar", "kiran@raghunss.edu", "pass", null));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "21B91A0502", "CSE", 3));
        membershipRepository.save(new UnitMembership(volunteer, unit));

        volunteerPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(volUser.getEmail()).password("pass").roles("VOLUNTEER").build();
    }

    @Test
    void submitClaim_validRequest_shouldCreatePendingEntry() {
        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(4.50), null, "External blood donation camp assistance"
        );

        ServiceHourResponse response = serviceHourService.submitClaim(request, volunteerPrincipal);

        assertNotNull(response.entryId());
        assertEquals("PENDING", response.status());
        assertEquals(BigDecimal.valueOf(4.50), response.hours());
        assertEquals("External blood donation camp assistance", response.description());
    }

    @Test
    void reviewClaim_approve_shouldCreditHours() {
        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(3.00), null, "Campus plantation maintenance"
        );
        ServiceHourResponse pending = serviceHourService.submitClaim(request, volunteerPrincipal);

        ServiceHourResponse reviewed = serviceHourService.reviewClaim(
            pending.entryId(),
            new ServiceHourReviewRequest("APPROVE", "Verified by unit coordinator"),
            adminPrincipal
        );

        assertEquals("APPROVED", reviewed.status());
        assertEquals(admin.getName(), reviewed.approvedByName());

        VolunteerServiceHoursSummary summary = serviceHourService.getMyServiceHours(volunteerPrincipal);
        assertEquals(3.0, summary.totalApprovedHours().doubleValue(), 0.001);
        assertEquals(1, summary.approvedCount());
        assertEquals(0, summary.pendingCount());
    }

    @Test
    void reviewClaim_reject_shouldMarkRejected() {
        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(2.00), null, "Unverified external activity"
        );
        ServiceHourResponse pending = serviceHourService.submitClaim(request, volunteerPrincipal);

        ServiceHourResponse reviewed = serviceHourService.reviewClaim(
            pending.entryId(),
            new ServiceHourReviewRequest("REJECT", "No attendance proof attached"),
            adminPrincipal
        );

        assertEquals("REJECTED", reviewed.status());
        assertTrue(reviewed.description().contains("[Reason: No attendance proof attached]"));

        VolunteerServiceHoursSummary summary = serviceHourService.getMyServiceHours(volunteerPrincipal);
        assertEquals(0.0, summary.totalApprovedHours().doubleValue(), 0.001);
        assertEquals(0, summary.approvedCount());
        assertEquals(0, summary.pendingCount());
    }

    @Test
    void getPendingClaims_asVolunteer_shouldBeDenied() {
        assertThrows(AccessDeniedException.class,
            () -> serviceHourService.getPendingClaims(volunteerPrincipal));
    }

    @Test
    void reviewClaim_selfReview_shouldThrowAccessDenied() {
        User officerUser = userRepository.save(new User("Dual Officer", "dualofficer@raghunss.edu", "pass", null));
        Volunteer dualVol = volunteerRepository.save(new Volunteer(officerUser, "21B91A0599", "CSE", 3));
        membershipRepository.save(new UnitMembership(dualVol, unit));

        UserDetails dualPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(officerUser.getEmail()).password("pass").authorities("PROGRAMME_OFFICER", "ROLE_PROGRAMME_OFFICER").build();

        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(2.00), null, "Self logged activity"
        );
        ServiceHourResponse pending = serviceHourService.submitClaim(request, dualPrincipal);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
            serviceHourService.reviewClaim(
                pending.entryId(),
                new ServiceHourReviewRequest("APPROVE", "Self approved"),
                dualPrincipal
            )
        );
        assertTrue(ex.getMessage().contains("Reviewer cannot approve or reject their own service hour claim"));
    }

    @Test
    void submitClaim_forEvent_whenAttendanceIsAbsent_shouldBeRejected() {
        Event pastEvent = eventRepository.save(new Event(
            unit, admin, "Past Service Event", "Description", "SERVICE",
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(14400), Instant.now().minusSeconds(7300),
            "Campus Grounds", 50
        ));
        AttendanceSession session = sessionRepository.save(new AttendanceSession(
            pastEvent, admin, Instant.now().minusSeconds(7000), Instant.now().minusSeconds(4000), "secret"
        ));
        recordRepository.save(new AttendanceRecord(session, volunteer, "MANUAL", "ABSENT"));

        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(1.00), pastEvent.getEventId(), "Claiming hours despite absence"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> serviceHourService.submitClaim(request, volunteerPrincipal));
        assertTrue(ex.getMessage().contains("ABSENT"));
    }

    @Test
    void reviewClaim_forEvent_derivesAccreditedHoursFromEventAndLinksAttendanceRecord() {
        Event pastEvent = eventRepository.save(new Event(
            unit, admin, "Past Two Hour Workshop", "Description", "TRAINING_WORKSHOP",
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(0), // 2 hours
            Instant.now().minusSeconds(14400), Instant.now().minusSeconds(7300),
            "Seminar Hall", 50
        ));
        AttendanceSession session = sessionRepository.save(new AttendanceSession(
            pastEvent, admin, Instant.now().minusSeconds(7000), Instant.now().minusSeconds(100), "secret"
        ));
        AttendanceRecord attRecord = recordRepository.save(new AttendanceRecord(session, volunteer, "QR", "PRESENT"));

        // Volunteer claims 1.0 hour, but event is configured for 2.0 hours
        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(1.00), pastEvent.getEventId(), "Attended 2 hour workshop"
        );
        ServiceHourResponse pending = serviceHourService.submitClaim(request, volunteerPrincipal);

        ServiceHourResponse approved = serviceHourService.reviewClaim(
            pending.entryId(),
            new ServiceHourReviewRequest("APPROVE", "Verified via attendance"),
            adminPrincipal
        );

        assertEquals("APPROVED", approved.status());
        // Derived from event.getServiceHours() = 2.00 hours
        assertEquals(BigDecimal.valueOf(2.0).setScale(2), approved.hours().setScale(2));

        ServiceHourEntry entry = serviceHourRepository.findById(approved.entryId()).orElseThrow();
        assertNotNull(entry.getAttendanceRecord());
        assertEquals(attRecord.getAttendanceId(), entry.getAttendanceRecord().getAttendanceId());
    }

    @Test
    void submitClaim_exceedingCeiling_shouldBeRejected() {
        ServiceHourClaimRequest request = new ServiceHourClaimRequest(
            BigDecimal.valueOf(65.00), null, "Excessive hours claim"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> serviceHourService.submitClaim(request, volunteerPrincipal));
        assertTrue(ex.getMessage().contains("cannot exceed 60 hours"));
    }
}
