package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.AttendanceDTOs.CreateSessionRequest;
import edu.college.nss.web.dto.AttendanceDTOs.SessionResponse;
import edu.college.nss.web.dto.ServiceHourDTOs.ServiceHourClaimRequest;
import edu.college.nss.web.dto.ServiceHourDTOs.ServiceHourResponse;
import edu.college.nss.web.dto.ServiceHourDTOs.ServiceHourReviewRequest;
import edu.college.nss.web.dto.UnitTransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Phase1DataIntegrityIntegrationTest {

    @Autowired private ServiceHourService serviceHourService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private NssUnitService nssUnitService;
    @Autowired private VolunteerService volunteerService;

    @Autowired private UserRepository userRepository;
    @Autowired private NssUnitRepository unitRepository;
    @Autowired private VolunteerRepository volunteerRepository;
    @Autowired private UnitMembershipRepository membershipRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private AttendanceSessionRepository sessionRepository;
    @Autowired private AttendanceRecordRepository recordRepository;
    @Autowired private ServiceHourEntryRepository serviceHourRepository;
    @Autowired private UnitTransferHistoryRepository transferHistoryRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private AttendanceCorrectionRepository correctionRepository;
    @Autowired private EventRegistrationRepository registrationRepository;
    @Autowired private VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired private AuthSessionRepository authSessionRepository;

    private User adminUser;
    private UserDetails adminPrincipal;
    private User po1User;
    private UserDetails po1Principal;
    private User po2User;
    private UserDetails po2Principal;

    private NssUnit unit1;
    private NssUnit unit2;
    private Volunteer volunteer;
    private UserDetails volunteerPrincipal;
    private Event openEvent;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        authSessionRepository.deleteAll();
        notificationRepository.deleteAll();
        announcementRepository.deleteAll();
        serviceHourRepository.deleteAll();
        correctionRepository.deleteAll();
        recordRepository.deleteAll();
        sessionRepository.deleteAll();
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        transferHistoryRepository.deleteAll();
        membershipRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        volunteerRepository.deleteAll();
        unitRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(new User("Admin Officer", "admin@raghunss.edu", "pass123", "9999999991"));
        adminPrincipal = new org.springframework.security.core.userdetails.User(
            adminUser.getEmail(), "pass123", List.of(new SimpleGrantedAuthority("ADMIN"), new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        po1User = userRepository.save(new User("PO Unit 1", "po1@raghunss.edu", "pass123", "9999999992"));
        po1Principal = new org.springframework.security.core.userdetails.User(
            po1User.getEmail(), "pass123", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER"), new SimpleGrantedAuthority("ROLE_PROGRAMME_OFFICER"))
        );

        po2User = userRepository.save(new User("PO Unit 2", "po2@raghunss.edu", "pass123", "9999999993"));
        po2Principal = new org.springframework.security.core.userdetails.User(
            po2User.getEmail(), "pass123", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER"), new SimpleGrantedAuthority("ROLE_PROGRAMME_OFFICER"))
        );

        unit1 = unitRepository.save(new NssUnit("Unit One", "REC-01", po1User, 50));
        unit2 = unitRepository.save(new NssUnit("Unit Two", "REC-02", po2User, 50));

        User volUser = userRepository.save(new User("Suresh Volunteer", "suresh@raghunss.edu", "pass123", "9999999994"));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "22B91A0501", "CSE", 2));
        volunteer.setStatus("ACTIVE");
        volunteer = volunteerRepository.save(volunteer);
        membershipRepository.save(new UnitMembership(volunteer, unit1));

        volunteerPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(volUser.getEmail()).password("pass123").roles("VOLUNTEER").build();

        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now().plusSeconds(3600);
        openEvent = new Event(unit1, po1User, "Special Cleanliness Event", "Event Desc", "SERVICE",
            start, end, start.minusSeconds(3600), start, "Campus", 50);
        openEvent.publish();
        openEvent.open();
        openEvent = eventRepository.save(openEvent);
    }

    @Test
    @DisplayName("I-01 & I-06: Event claim approval without verified PRESENT attendance record must throw IllegalStateException")
    void eventClaimApproval_withoutPresentAttendance_throwsIllegalState() {
        ServiceHourClaimRequest claimReq = new ServiceHourClaimRequest(
            BigDecimal.valueOf(2.0),
            openEvent.getEventId(),
            "Claiming hours for Cleanliness Drive",
            "REGULAR_ACTIVITY",
            "Proof note",
            LocalDate.now()
        );

        ServiceHourResponse pendingClaim = serviceHourService.submitClaim(claimReq, volunteerPrincipal);
        assertEquals("PENDING", pendingClaim.status());

        // Attempting to approve event claim when NO PRESENT attendance record exists
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            serviceHourService.reviewClaim(
                pendingClaim.entryId(),
                new ServiceHourReviewRequest("APPROVE", "Attempted approval without attendance"),
                po1Principal
            )
        );

        assertTrue(ex.getMessage().contains("Cannot approve event service hours without a verified PRESENT attendance record"));

        // Verify status remains PENDING in database
        ServiceHourEntry entry = serviceHourRepository.findById(pendingClaim.entryId()).orElseThrow();
        assertEquals("PENDING", entry.getStatus());
        assertNull(entry.getAttendanceRecord());
    }

    @Test
    @DisplayName("I-01 & I-06: Event claim approval with verified PRESENT attendance record succeeds and links record")
    void eventClaimApproval_withPresentAttendance_succeedsAndLinksRecord() {
        // Record PRESENT attendance
        AttendanceSession session = sessionRepository.save(new AttendanceSession(
            openEvent, po1User, Instant.now().minusSeconds(1800), Instant.now().plusSeconds(1800), "secret-key"
        ));
        AttendanceRecord attRecord = recordRepository.save(new AttendanceRecord(session, volunteer, "MANUAL", "PRESENT"));

        ServiceHourClaimRequest claimReq = new ServiceHourClaimRequest(
            BigDecimal.valueOf(1.0),
            openEvent.getEventId(),
            "Claiming hours with verified attendance",
            "REGULAR_ACTIVITY",
            "Proof note",
            LocalDate.now()
        );

        ServiceHourResponse pendingClaim = serviceHourService.submitClaim(claimReq, volunteerPrincipal);
        assertEquals("PENDING", pendingClaim.status());

        ServiceHourResponse approvedClaim = serviceHourService.reviewClaim(
            pendingClaim.entryId(),
            new ServiceHourReviewRequest("APPROVE", "Verified via attendance record"),
            po1Principal
        );

        assertEquals("APPROVED", approvedClaim.status());
        assertEquals(openEvent.getServiceHours().setScale(2), approvedClaim.hours().setScale(2));

        ServiceHourEntry entry = serviceHourRepository.findById(approvedClaim.entryId()).orElseThrow();
        assertEquals("APPROVED", entry.getStatus());
        assertNotNull(entry.getAttendanceRecord());
        assertEquals(attRecord.getAttendanceId(), entry.getAttendanceRecord().getAttendanceId());
    }

    @Test
    @DisplayName("I-21: Service hour rejection requires non-blank reason")
    void serviceHourRejection_withoutReason_throwsIllegalArgument() {
        ServiceHourClaimRequest claimReq = new ServiceHourClaimRequest(
            BigDecimal.valueOf(2.0),
            null,
            "Non-event community outreach claim",
            "COMMUNITY_OUTREACH",
            "Proof note",
            LocalDate.now()
        );

        ServiceHourResponse pendingClaim = serviceHourService.submitClaim(claimReq, volunteerPrincipal);

        // Blank reason
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            serviceHourService.reviewClaim(
                pendingClaim.entryId(),
                new ServiceHourReviewRequest("REJECT", "   "),
                po1Principal
            )
        );
        assertTrue(ex.getMessage().contains("Please provide a reason"));

        // Null reason
        assertThrows(IllegalArgumentException.class, () ->
            serviceHourService.reviewClaim(
                pendingClaim.entryId(),
                new ServiceHourReviewRequest("REJECT", null),
                po1Principal
            )
        );

        // Valid reason succeeds
        ServiceHourResponse rejected = serviceHourService.reviewClaim(
            pendingClaim.entryId(),
            new ServiceHourReviewRequest("REJECT", "Insufficient evidence provided for community service"),
            po1Principal
        );
        assertEquals("REJECTED", rejected.status());
        assertTrue(rejected.description().contains("[Reason: Insufficient evidence provided for community service]"));
    }

    @Test
    @DisplayName("I-03: Repeated transfers between units succeed without unique constraint violation")
    void volunteerRepeatedTransfers_preservesHistoricalRecordsWithoutUniqueConstraintFailure() {
        // Volunteer starts in unit1
        assertEquals(1, membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unit1.getUnitId()).size());

        // 1. Transfer Unit 1 -> Unit 2
        nssUnitService.transferVolunteer(unit1.getUnitId(), new UnitTransferRequest(volunteer.getVolunteerId(), unit2.getUnitId(), "Move to Unit 2"), adminPrincipal);
        assertEquals(unit2.getUnitId(), membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId()).orElseThrow().getUnit().getUnitId());

        // 2. Transfer Unit 2 -> Unit 1 (moved back)
        nssUnitService.transferVolunteer(unit2.getUnitId(), new UnitTransferRequest(volunteer.getVolunteerId(), unit1.getUnitId(), "Return to Unit 1"), adminPrincipal);
        assertEquals(unit1.getUnitId(), membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId()).orElseThrow().getUnit().getUnitId());

        // 3. Transfer Unit 1 -> Unit 2 again (repeated historical transition)
        assertDoesNotThrow(() ->
            nssUnitService.transferVolunteer(unit1.getUnitId(), new UnitTransferRequest(volunteer.getVolunteerId(), unit2.getUnitId(), "Move to Unit 2 again"), adminPrincipal)
        );

        assertEquals(unit2.getUnitId(), membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId()).orElseThrow().getUnit().getUnitId());

        // Verify total membership records: 1 active, 3 inactive history
        List<UnitMembership> allMemberships = membershipRepository.findAll().stream()
            .filter(m -> m.getVolunteer().getVolunteerId().equals(volunteer.getVolunteerId()))
            .toList();
        assertEquals(4, allMemberships.size());
        assertEquals(1, allMemberships.stream().filter(UnitMembership::getIsActive).count());
    }

    @Test
    @DisplayName("I-04: Unit capacity check rejects assignments when unit is full")
    void unitCapacity_rejectsWhenFull() {
        NssUnit smallUnit = unitRepository.save(new NssUnit("Small Unit", "REC-09", po1User, 1));

        // Add 1 volunteer -> reaches capacity
        User u1 = userRepository.save(new User("Vol One", "vol1@raghunss.edu", "pass", null));
        Volunteer v1 = volunteerRepository.save(new Volunteer(u1, "22B91A0511", "CSE", 2));
        nssUnitService.addMemberToUnit(smallUnit.getUnitId(), v1.getVolunteerId(), adminPrincipal);

        // Attempt to add 2nd volunteer -> should throw IllegalStateException
        User u2 = userRepository.save(new User("Vol Two", "vol2@raghunss.edu", "pass", null));
        Volunteer v2 = volunteerRepository.save(new Volunteer(u2, "22B91A0512", "CSE", 2));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            nssUnitService.addMemberToUnit(smallUnit.getUnitId(), v2.getVolunteerId(), adminPrincipal)
        );
        assertTrue(ex.getMessage().contains("is full"));
    }

    @Test
    @DisplayName("I-05: Attendance session start and expiry time bounds validation")
    void openAttendanceSession_scheduleBoundsValidation() {
        Instant eventStart = openEvent.getStartAt();
        Instant eventEnd = openEvent.getEndAt();

        // Starts more than 2 hours before event start -> invalid
        CreateSessionRequest tooEarly = new CreateSessionRequest(
            eventStart.minusSeconds(7300), eventEnd
        );
        IllegalArgumentException exEarly = assertThrows(IllegalArgumentException.class, () ->
            attendanceService.openSession(openEvent.getEventId(), tooEarly, adminPrincipal)
        );
        assertTrue(exEarly.getMessage().contains("Session start time cannot precede event start"));

        // Expires more than 4 hours after event end -> invalid
        CreateSessionRequest tooLate = new CreateSessionRequest(
            eventStart, eventEnd.plusSeconds(14500)
        );
        IllegalArgumentException exLate = assertThrows(IllegalArgumentException.class, () ->
            attendanceService.openSession(openEvent.getEventId(), tooLate, adminPrincipal)
        );
        assertTrue(exLate.getMessage().contains("Session expiration time cannot exceed event end"));
    }
}

