package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.exception.AttendanceSessionExpiredException;
import edu.college.nss.exception.DuplicateAttendanceException;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.AttendanceDTOs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AttendanceIntegrationTest {
    @Autowired AttendanceService attendanceService;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired AttendanceRecordRepository recordRepository;
    @Autowired AttendanceCorrectionRepository correctionRepository;
    @Autowired ServiceHourEntryRepository serviceHourRepository;
    @Autowired EventRepository eventRepository;
    @Autowired EventRegistrationRepository registrationRepository;
    @Autowired VolunteerRepository volunteerRepository;
    @Autowired VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired UnitTransferHistoryRepository transferHistoryRepository;
    @Autowired NssUnitRepository unitRepository;
    @Autowired UserRepository userRepository;
    @Autowired UnitMembershipRepository membershipRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AnnouncementRepository announcementRepository;

    private User admin;
    private UserDetails adminPrincipal;
    private User po;
    private UserDetails poPrincipal;
    private NssUnit unit;
    private Event event;
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
        statusHistoryRepository.deleteAll();
        transferHistoryRepository.deleteAll();
        volunteerRepository.deleteAll();
        unitRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Admin User", "admin@raghunss.edu", "pass", null));
        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ADMIN")));

        po = userRepository.save(new User("Unit Officer", "officer@raghunss.edu", "pass", null));
        poPrincipal = new org.springframework.security.core.userdetails.User(
            po.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));

        unit = unitRepository.save(new NssUnit("Unit 1", "REC-01", po));

        Instant start = Instant.now().minusSeconds(1800);
        Instant end = Instant.now().plusSeconds(1800);
        event = new Event(unit, po, "Beach Cleanup", "Campus drive", "SERVICE",
            start, end, start.minusSeconds(3600), start, "Beach Road", 50);
        event.publish();
        event.open();
        event = eventRepository.save(event);

        User volUser = userRepository.save(new User("Ramesh Kumar", "ramesh@raghunss.edu", "pass", null));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "21B91A0501", "CSE", 3));
        membershipRepository.save(new UnitMembership(volunteer, unit));

        volunteerPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(volUser.getEmail()).password("pass").roles("VOLUNTEER").build();
    }

    @Test
    void checkIn_validToken_shouldRecordAttendanceAndCreditHours() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest sessionReq = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), sessionReq, adminPrincipal);

        CheckInResponse checkIn = attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        assertEquals("PRESENT", checkIn.status());
        assertEquals("QR", checkIn.checkInMethod());
        assertTrue(recordRepository.existsBySession_SessionIdAndVolunteer_VolunteerId(session.sessionId(), volunteer.getVolunteerId()));

        var hours = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertTrue(hours.doubleValue() > 0);
    }

    @Test
    void checkIn_unregisteredVolunteer_shouldBeDenied() {
        CreateSessionRequest sessionReq = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), sessionReq, adminPrincipal);

        assertThrows(AccessDeniedException.class,
            () -> attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal));
    }

    @Test
    void checkIn_duplicateScans_shouldThrowDuplicateAttendanceException() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest sessionReq = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), sessionReq, adminPrincipal);

        attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        assertThrows(DuplicateAttendanceException.class,
            () -> attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal));
    }

    @Test
    void checkIn_closedSession_shouldThrowSessionExpired() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest sessionReq = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), sessionReq, adminPrincipal);
        attendanceService.closeSession(session.sessionId(), adminPrincipal);

        assertThrows(AttendanceSessionExpiredException.class,
            () -> attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal));
    }

    @Test
    void correctAttendance_shouldLeaveOriginalUnchangedUntilApproved() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest sessionReq = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), sessionReq, adminPrincipal);
        CheckInResponse checkIn = attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        CorrectionResponse correction = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("EXCUSED", "Medical certificate submitted to NSS Unit Officer."),
            poPrincipal
        );

        assertEquals("PENDING", correction.status());
        assertEquals("PRESENT", correction.previousStatus());
        assertEquals("EXCUSED", correction.newStatus());

        // Invariant: AttendanceRecord status MUST NOT change while correction is PENDING
        AttendanceRecord pendingRecord = recordRepository.findById(checkIn.attendanceId()).orElseThrow();
        assertEquals("PRESENT", pendingRecord.getStatus());

        // Reject correction -> original status MUST remain PRESENT
        attendanceService.reviewCorrection(correction.correctionId(), new edu.college.nss.web.dto.CorrectionReviewRequest(false, "Rejected by PO"), adminPrincipal);
        AttendanceRecord rejectedRecord = recordRepository.findById(checkIn.attendanceId()).orElseThrow();
        assertEquals("PRESENT", rejectedRecord.getStatus());

        // New correction -> approve it -> status must now update to EXCUSED
        CorrectionResponse correction2 = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("EXCUSED", "Valid document resubmitted"),
            poPrincipal
        );
        attendanceService.reviewCorrection(correction2.correctionId(), new edu.college.nss.web.dto.CorrectionReviewRequest(true, "Approved"), adminPrincipal);
        AttendanceRecord approvedRecord = recordRepository.findById(checkIn.attendanceId()).orElseThrow();
        assertEquals("EXCUSED", approvedRecord.getStatus());
    }

    @Test
    void openSession_whenActiveSessionAlreadyOpen_shouldThrowIllegalState() {
        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(10), Instant.now().plusSeconds(300));
        attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        assertThrows(IllegalStateException.class, () ->
            attendanceService.openSession(event.getEventId(), req, adminPrincipal));
    }

    @Test
    void checkIn_beforeSessionStarts_shouldThrowSessionNotStarted() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().plusSeconds(60), Instant.now().plusSeconds(600));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        assertThrows(AttendanceSessionExpiredException.class, () ->
            attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal));
    }

    @Test
    void manualCheckIn_unregisteredVolunteer_shouldBeDenied() {
        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(10), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        assertThrows(AccessDeniedException.class, () ->
            attendanceService.manualCheckIn(session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "PRESENT"), adminPrincipal));
    }

    @Test
    void openSession_onCompletedEvent_shouldThrowIllegalState() {
        Event completedEvent = new Event(unit, admin, "Tree Plantation", "Completed Drive", "SERVICE",
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(10800), Instant.now().minusSeconds(7200), "Campus Grounds", 50);
        completedEvent.publish();
        completedEvent.open();
        completedEvent.close();
        completedEvent.complete();
        completedEvent = eventRepository.save(completedEvent);

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(10), Instant.now().plusSeconds(300));
        Event finalCompletedEvent = completedEvent;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            attendanceService.openSession(finalCompletedEvent.getEventId(), req, adminPrincipal));
        assertTrue(ex.getMessage().contains("OPEN events"));
    }

    @Test
    void manualCheckIn_beforeSessionStarts_shouldThrowSessionNotStarted() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().plusSeconds(60), Instant.now().plusSeconds(600));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        AttendanceSessionExpiredException ex = assertThrows(AttendanceSessionExpiredException.class, () ->
            attendanceService.manualCheckIn(session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "PRESENT"), adminPrincipal));
        assertTrue(ex.getMessage().contains("not started yet"));
    }

    @Test
    void manualCheckIn_validTiming_shouldRecordAttendance() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        CheckInResponse response = attendanceService.manualCheckIn(
            session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "PRESENT"), adminPrincipal);

        assertEquals("PRESENT", response.status());
        assertEquals("MANUAL_COORDINATOR", response.checkInMethod());
        assertTrue(recordRepository.existsBySession_SessionIdAndVolunteer_VolunteerId(session.sessionId(), volunteer.getVolunteerId()));
    }

    @Test
    void manualCheckIn_selfAward_shouldThrowAccessDenied() {
        // Volunteer who is also a student leader
        User leaderUser = userRepository.save(new User("Leader Vol", "leader@raghunss.edu", "pass", null));
        Volunteer leaderVolunteer = volunteerRepository.save(new Volunteer(leaderUser, "21B91A0599", "CSE", 3));
        membershipRepository.save(new UnitMembership(leaderVolunteer, unit));
        registrationRepository.save(new EventRegistration(event, leaderVolunteer));

        UserDetails leaderPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(leaderUser.getEmail()).password("pass").authorities("STUDENT_LEADER", "ATTENDANCE_MANAGE").build();

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
            attendanceService.manualCheckIn(
                session.sessionId(),
                new ManualCheckInRequest(leaderVolunteer.getVolunteerId(), "PRESENT"),
                leaderPrincipal
            )
        );
        assertTrue(ex.getMessage().contains("Self-check-in via manual attendance is strictly prohibited"));
    }

    @Test
    void manualCheckIn_statusChangeWithoutReason_shouldThrowIllegalArgument() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        // Initial check-in PRESENT
        attendanceService.manualCheckIn(
            session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "PRESENT"), adminPrincipal);

        // Modify to ABSENT with blank reason -> should throw IllegalArgumentException
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            attendanceService.manualCheckIn(
                session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "ABSENT", "  "), adminPrincipal));
        assertTrue(ex.getMessage().contains("mandatory when modifying existing attendance status"));
    }

    @Test
    void manualCheckIn_demoteToAbsent_shouldRevokeServiceHours() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        // Initial check-in PRESENT
        attendanceService.manualCheckIn(
            session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "PRESENT"), adminPrincipal);

        var hoursBefore = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertTrue(hoursBefore.doubleValue() > 0);

        // Demote to ABSENT with valid reason
        attendanceService.manualCheckIn(
            session.sessionId(),
            new ManualCheckInRequest(volunteer.getVolunteerId(), "ABSENT", "Left early without authorization"),
            adminPrincipal
        );

        var hoursAfter = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertEquals(0.0, hoursAfter.doubleValue(), 0.001);

        // Verify ServiceHourEntry status is REJECTED
        List<ServiceHourEntry> entries = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteer.getVolunteerId());
        assertEquals(1, entries.size());
        assertEquals("REJECTED", entries.get(0).getStatus());
    }

    @Test
    void reviewCorrection_alreadyReviewed_shouldThrowIllegalState() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);
        CheckInResponse checkIn = attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        CorrectionResponse correction = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("EXCUSED", "Medical leave verified"),
            poPrincipal
        );

        attendanceService.reviewCorrection(correction.correctionId(), new edu.college.nss.web.dto.CorrectionReviewRequest(true, "Approved"), adminPrincipal);

        // Re-review must fail
        assertThrows(IllegalStateException.class, () ->
            attendanceService.reviewCorrection(correction.correctionId(), new edu.college.nss.web.dto.CorrectionReviewRequest(false, "Re-review attempt"), adminPrincipal));
    }

    @Test
    void reviewCorrection_selfReview_shouldThrowAccessDenied() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);
        CheckInResponse checkIn = attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        // PO submits correction
        CorrectionResponse correction = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("EXCUSED", "Medical leave"),
            poPrincipal
        );

        // PO attempts to review own correction -> AccessDeniedException
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
            attendanceService.reviewCorrection(correction.correctionId(), new edu.college.nss.web.dto.CorrectionReviewRequest(true, "Self approved"), poPrincipal));
        assertTrue(ex.getMessage().contains("Reviewer cannot review an attendance correction they submitted"));
    }

    @Test
    void reviewCorrection_targetVolunteerReview_shouldThrowAccessDenied() {
        // Volunteer who is also a Programme Officer on another unit
        User dualUser = userRepository.save(new User("Dual Role User", "dual@raghunss.edu", "pass", null));
        Volunteer dualVol = volunteerRepository.save(new Volunteer(dualUser, "21B91A0588", "CSE", 3));
        membershipRepository.save(new UnitMembership(dualVol, unit));
        registrationRepository.save(new EventRegistration(event, dualVol));

        UserDetails dualPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(dualUser.getEmail()).password("pass").authorities("ADMIN", "PROGRAMME_OFFICER").build();

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        CheckInResponse checkIn = attendanceService.manualCheckIn(
            session.sessionId(), new ManualCheckInRequest(dualVol.getVolunteerId(), "ABSENT"), adminPrincipal);

        // PO submits correction for dualUser
        CorrectionResponse correction = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("PRESENT", "Attended event"),
            poPrincipal
        );

        // dualUser attempts to review correction on their own attendance record -> AccessDeniedException
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
            attendanceService.reviewCorrection(correction.correctionId(), new edu.college.nss.web.dto.CorrectionReviewRequest(true, "Self review"), dualPrincipal));
        assertTrue(ex.getMessage().contains("Reviewer cannot review an attendance correction for their own record"));
    }

    @Test
    void reviewCorrection_absentToPresent_shouldCreateApprovedServiceHours() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);

        // Marked ABSENT initially
        CheckInResponse checkIn = attendanceService.manualCheckIn(
            session.sessionId(), new ManualCheckInRequest(volunteer.getVolunteerId(), "ABSENT"), adminPrincipal);

        var hoursInitially = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertEquals(0.0, hoursInitially.doubleValue(), 0.001);

        // PO requests correction to PRESENT
        CorrectionResponse correction = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("PRESENT", "Attended and completed duty, missed initial check-in"),
            poPrincipal
        );

        // Admin approves correction
        attendanceService.reviewCorrection(
            correction.correctionId(),
            new edu.college.nss.web.dto.CorrectionReviewRequest(true, "Verified on roster"),
            adminPrincipal
        );

        // Invariant: Volunteer MUST now have approved service hours!
        var hoursAfter = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertTrue(hoursAfter.doubleValue() > 0);

        List<ServiceHourEntry> entries = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteer.getVolunteerId());
        assertEquals(1, entries.size());
        assertEquals("APPROVED", entries.get(0).getStatus());
    }

    @Test
    void reviewCorrection_presentToAbsent_shouldRevokeServiceHours() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);
        CheckInResponse checkIn = attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        var hoursInitially = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertTrue(hoursInitially.doubleValue() > 0);

        // PO submits correction to ABSENT
        CorrectionResponse correction = attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("ABSENT", "Proxy check-in detected by audit"),
            poPrincipal
        );

        // Admin approves correction
        attendanceService.reviewCorrection(
            correction.correctionId(),
            new edu.college.nss.web.dto.CorrectionReviewRequest(true, "Proxy confirmed"),
            adminPrincipal
        );

        var hoursAfter = serviceHourRepository.sumApprovedHoursForVolunteer(volunteer.getVolunteerId());
        assertEquals(0.0, hoursAfter.doubleValue(), 0.001);

        List<ServiceHourEntry> entries = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteer.getVolunteerId());
        assertEquals(1, entries.size());
        assertEquals("REJECTED", entries.get(0).getStatus());
    }

    @Test
    void correctAttendance_duplicatePendingCorrection_shouldThrowIllegalState() {
        registrationRepository.save(new EventRegistration(event, volunteer));

        CreateSessionRequest req = new CreateSessionRequest(
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        SessionResponse session = attendanceService.openSession(event.getEventId(), req, adminPrincipal);
        CheckInResponse checkIn = attendanceService.checkInWithQr(new CheckInRequest(session.qrToken()), volunteerPrincipal);

        attendanceService.correctAttendance(
            checkIn.attendanceId(),
            new CorrectionRequest("EXCUSED", "Medical document pending"),
            poPrincipal
        );

        // Second correction request while first is still PENDING -> should throw IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            attendanceService.correctAttendance(
                checkIn.attendanceId(),
                new CorrectionRequest("ABSENT", "Another correction"),
                poPrincipal
            )
        );
        assertTrue(ex.getMessage().contains("already pending review"));
    }
}
