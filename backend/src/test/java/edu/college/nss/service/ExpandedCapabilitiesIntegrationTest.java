package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.*;
import edu.college.nss.web.dto.AttendanceDTOs.CorrectionRequest;
import edu.college.nss.web.dto.AttendanceDTOs.CorrectionResponse;
import edu.college.nss.web.dto.ServiceHourDTOs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ExpandedCapabilitiesIntegrationTest {

    @Autowired VolunteerService volunteerService;
    @Autowired NssUnitService nssUnitService;
    @Autowired EventService eventService;
    @Autowired AttendanceService attendanceService;
    @Autowired ServiceHourService serviceHourService;
    @Autowired NotificationService notificationService;

    @Autowired VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired UnitTransferHistoryRepository transferHistoryRepository;
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

    private User adminUser;
    private UserDetails adminPrincipal;
    private NssUnit unit1;
    private NssUnit unit2;
    private Volunteer volunteer;
    private UserDetails volunteerPrincipal;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        transferHistoryRepository.deleteAll();
        notificationRepository.deleteAll();
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

        adminUser = userRepository.save(new User("Admin PO", "admin@raghunss.edu", "pass", null));
        adminPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(adminUser.getEmail()).password("pass").roles("ADMIN").build();

        unit1 = unitRepository.save(new NssUnit("NSS Unit 1", "ENG", adminUser));
        unit2 = unitRepository.save(new NssUnit("NSS Unit 2", "MED", adminUser));

        User volUser = userRepository.save(new User("Jane Doe", "jane@raghunss.edu", "pass", null));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "COL-501", "CS", 2));
        membershipRepository.save(new UnitMembership(volunteer, unit1));

        volunteerPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(volUser.getEmail()).password("pass").roles("VOLUNTEER").build();
    }

    @Test
    void volunteerStatusWorkflow_shouldRecordHistoryAndNotify() {
        VolunteerStatusUpdateRequest req = new VolunteerStatusUpdateRequest("ACTIVE", "Completed orientation and document verification");
        VolunteerResponse updated = volunteerService.updateVolunteerStatus(volunteer.getVolunteerId(), req, adminPrincipal);

        assertEquals("ACTIVE", updated.status());

        List<VolunteerStatusHistoryResponse> history = volunteerService.getVolunteerStatusHistory(volunteer.getVolunteerId(), adminPrincipal);
        assertEquals(1, history.size());
        assertEquals("ACTIVE", history.get(0).fromStatus());
        assertEquals("ACTIVE", history.get(0).toStatus());
        assertEquals("Completed orientation and document verification", history.get(0).reason());

        long unread = notificationService.getUnreadCount(volunteerPrincipal);
        assertTrue(unread >= 1);
    }

    @Test
    void unitTransferWorkflow_shouldRecordHistoryAndReassignMembership() {
        UnitTransferRequest req = new UnitTransferRequest(
            volunteer.getVolunteerId(),
            unit2.getUnitId(),
            "Departmental relocation to Unit 2"
        );

        nssUnitService.transferVolunteer(unit1.getUnitId(), req, adminPrincipal);

        List<UnitTransferHistoryResponse> transfers = nssUnitService.getVolunteerTransferHistory(volunteer.getVolunteerId());
        assertEquals(1, transfers.size());
        assertEquals("NSS Unit 1", transfers.get(0).fromUnitName());
        assertEquals("NSS Unit 2", transfers.get(0).toUnitName());
        assertEquals("Departmental relocation to Unit 2", transfers.get(0).reason());

        UnitStatsResponse stats1 = nssUnitService.getUnitStats(unit1.getUnitId());
        UnitStatsResponse stats2 = nssUnitService.getUnitStats(unit2.getUnitId());
        assertEquals(0, stats1.activeVolunteers());
        assertEquals(1, stats2.activeVolunteers());
    }

    @Test
    void waitlistAndAutoPromotion_shouldPromoteHighestPriorityWaitlistedVolunteerOnCancellation() {
        // Prepare 2 volunteers
        Volunteer v1 = volunteer;
        volunteerService.updateVolunteerStatus(v1.getVolunteerId(), new VolunteerStatusUpdateRequest("ACTIVE", "Approved"), adminPrincipal);

        User u2 = userRepository.save(new User("Waitlist User", "wl@raghunss.edu", "pass", null));
        Volunteer v2 = volunteerRepository.save(new Volunteer(u2, "COL-502", "IT", 2));
        volunteerService.updateVolunteerStatus(v2.getVolunteerId(), new VolunteerStatusUpdateRequest("ACTIVE", "Approved"), adminPrincipal);
        membershipRepository.save(new UnitMembership(v2, unit1));

        UserDetails v2Principal = org.springframework.security.core.userdetails.User
            .withUsername(u2.getEmail()).password("pass").roles("VOLUNTEER").build();

        // Create an event with capacity 1
        Instant now = Instant.now();
        Event event = new Event(unit1, adminUser, "Single Seat Workshop", "Workshop desc", "SERVICE",
            now.plusSeconds(3600), now.plusSeconds(7200), now.minusSeconds(60), now.plusSeconds(1800),
            "Auditorium", 1);
        event.setStatus("OPEN");
        event = eventRepository.save(event);

        // V1 registers and gets CONFIRMED
        EventRegistrationResponse reg1 = eventService.register(event.getEventId(), new RegistrationRequest(v1.getVolunteerId()), volunteerPrincipal);
        assertEquals("CONFIRMED", reg1.status());
        assertNull(reg1.waitlistPosition());

        // V2 joins waitlist and gets WAITLISTED at position 1
        EventRegistrationResponse reg2 = eventService.register(event.getEventId(), new RegistrationRequest(v2.getVolunteerId(), true), v2Principal);
        assertEquals("WAITLISTED", reg2.status());
        assertEquals(1, reg2.waitlistPosition());

        // V1 cancels registration
        eventService.cancelRegistration(event.getEventId(), volunteerPrincipal);

        // V2 should now be automatically promoted to CONFIRMED
        EventRegistrationResponse v2Reg = eventService.myRegistration(event.getEventId(), v2Principal);
        assertNotNull(v2Reg);
        assertEquals("CONFIRMED", v2Reg.status());
        assertNull(v2Reg.waitlistPosition());
    }

    @Test
    void serviceHours_shouldCalculate120HourMilestoneProgressAndCategoryBreakdown() {
        volunteerService.updateVolunteerStatus(volunteer.getVolunteerId(), new VolunteerStatusUpdateRequest("ACTIVE", "Approved"), adminPrincipal);

        // Submit claim for 30 hours Regular
        ServiceHourClaimRequest claim1 = new ServiceHourClaimRequest(
            BigDecimal.valueOf(30.00), null, "Campus Green Initiative",
            "REGULAR_ACTIVITY", "Verified by team leader", LocalDate.now()
        );
        ServiceHourResponse c1 = serviceHourService.submitClaim(claim1, volunteerPrincipal);
        serviceHourService.reviewClaim(c1.entryId(), new ServiceHourReviewRequest("APPROVE", null), adminPrincipal);

        // Submit claim for 30 hours Community Outreach
        ServiceHourClaimRequest claim2 = new ServiceHourClaimRequest(
            BigDecimal.valueOf(30.00), null, "Village Literacy Camp",
            "COMMUNITY_OUTREACH", "Gram Panchayat letter", LocalDate.now()
        );
        ServiceHourResponse c2 = serviceHourService.submitClaim(claim2, volunteerPrincipal);
        serviceHourService.reviewClaim(c2.entryId(), new ServiceHourReviewRequest("APPROVE", null), adminPrincipal);

        VolunteerServiceHoursSummary summary = serviceHourService.getMyServiceHours(volunteerPrincipal);
        assertNotNull(summary);
        assertEquals(BigDecimal.valueOf(60.00).setScale(2), summary.totalApprovedHours().setScale(2));
        assertEquals(50.0, summary.progressPercentage());
        assertEquals(30.0, summary.regularHours());
        assertEquals(30.0, summary.communityHours());
        assertEquals(0.0, summary.otherHours());
        assertEquals(2, summary.approvedCount());
    }

    @Test
    void notificationCenter_shouldMarkAllAsRead() {
        notificationService.sendNotification(volunteer.getUser(), "Notice 1", "Body 1", "GENERAL", "/");
        notificationService.sendNotification(volunteer.getUser(), "Notice 2", "Body 2", "GENERAL", "/");

        assertEquals(2, notificationService.getUnreadCount(volunteerPrincipal));

        notificationService.markAllAsRead(volunteerPrincipal);
        assertEquals(0, notificationService.getUnreadCount(volunteerPrincipal));
    }
}
