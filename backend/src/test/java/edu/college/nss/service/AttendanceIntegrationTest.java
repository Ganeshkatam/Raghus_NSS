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
    private NssUnit unit;
    private UserDetails adminPrincipal;
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

        unit = unitRepository.save(new NssUnit("Unit 1", "REC-01", admin));

        Instant start = Instant.now().minusSeconds(1800);
        Instant end = Instant.now().plusSeconds(1800);
        event = new Event(unit, admin, "Beach Cleanup", "Campus drive", "SERVICE",
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
            adminPrincipal
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
            adminPrincipal
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
}
