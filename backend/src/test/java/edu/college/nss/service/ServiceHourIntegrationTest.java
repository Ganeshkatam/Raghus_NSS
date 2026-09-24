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
}
