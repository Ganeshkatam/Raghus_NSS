package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.UserController;
import edu.college.nss.web.dto.*;
import edu.college.nss.web.dto.AttendanceDTOs.CreateSessionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AuditAndGovernanceIntegrationTest {

    @Autowired private AuditService auditService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserController userController;
    @Autowired private VolunteerService volunteerService;
    @Autowired private EventService eventService;
    @Autowired private AttendanceService attendanceService;

    @Autowired private UserRepository userRepository;
    @Autowired private NssUnitRepository unitRepository;
    @Autowired private VolunteerRepository volunteerRepository;
    @Autowired private UnitMembershipRepository membershipRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private AttendanceSessionRepository sessionRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired private UnitTransferHistoryRepository transferHistoryRepository;
    @Autowired private AttendanceRecordRepository recordRepository;
    @Autowired private AttendanceCorrectionRepository correctionRepository;
    @Autowired private ServiceHourEntryRepository serviceHourRepository;
    @Autowired private EventRegistrationRepository registrationRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User admin;
    private UserDetails adminPrincipal;
    private Volunteer volunteer;
    private NssUnit unit;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
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

        admin = userRepository.save(new User("Admin Governance", "admin-gov@raghunss.edu", "pass123", null));
        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "pass123", List.of(new SimpleGrantedAuthority("ADMIN"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        unit = unitRepository.save(new NssUnit("Gov Unit 1", "GOV-01", admin));

        User volUser = userRepository.save(new User("Gov Volunteer", "gov-vol@raghunss.edu", "pass123", null));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "GOV-V001", "CSE", 2));
        membershipRepository.save(new UnitMembership(volunteer, unit));

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities())
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void auditLogging_recordsAdministrativeAction_andExposesViaEndpoint() {
        // Change user status via controller
        userController.updateStatus(admin.getUserId(), Map.of("status", "ACTIVE", "reason", "Annual account audit"), adminPrincipal);

        List<AuditLogItem> logs = userController.getAuditLogs();
        assertFalse(logs.isEmpty());

        boolean hasStatusLog = logs.stream().anyMatch(l ->
            "USER".equals(l.entityType()) &&
            "USER_STATUS_CHANGE".equals(l.action()) &&
            "Annual account audit".equals(l.reason())
        );
        assertTrue(hasStatusLog, "Audit log should contain administrative user status change");
    }

    @Test
    void volunteerStateMachine_enforcesLegalTransitions_andRejectsIllegalTransitions() {
        // Legal transition: ACTIVE -> SUSPENDED
        VolunteerResponse suspended = volunteerService.updateVolunteerStatus(
            volunteer.getVolunteerId(),
            new VolunteerStatusUpdateRequest("SUSPENDED", "Disciplinary review"),
            adminPrincipal
        );
        assertEquals("SUSPENDED", suspended.status());

        // Legal transition: SUSPENDED -> ACTIVE
        VolunteerResponse reinstated = volunteerService.updateVolunteerStatus(
            volunteer.getVolunteerId(),
            new VolunteerStatusUpdateRequest("ACTIVE", "Reinstated"),
            adminPrincipal
        );
        assertEquals("ACTIVE", reinstated.status());

        // Legal transition: ACTIVE -> ALUMNI
        VolunteerResponse alumni = volunteerService.updateVolunteerStatus(
            volunteer.getVolunteerId(),
            new VolunteerStatusUpdateRequest("ALUMNI", "Graduated batch 2026"),
            adminPrincipal
        );
        assertEquals("ALUMNI", alumni.status());

        // Illegal transition: ALUMNI -> ACTIVE (terminal state)
        assertThrows(IllegalStateException.class, () ->
            volunteerService.updateVolunteerStatus(
                volunteer.getVolunteerId(),
                new VolunteerStatusUpdateRequest("ACTIVE", "Attempted revival"),
                adminPrincipal
            )
        );
    }

    @Test
    void eventCompletion_rejectsCompletion_whenAttendanceSessionIsOpen() {
        Instant now = Instant.now();
        Event event = new Event(
            unit, admin, "Audit Cleanup Event", "Community task", "CLEANUP",
            now.minusSeconds(7200), now.plusSeconds(3600),
            now.minusSeconds(10800), now.minusSeconds(7200),
            "Campus Grounds", 50
        );
        event.setStatus("OPEN");
        event = eventRepository.save(event);

        // Open an attendance session for this event
        AttendanceDTOs.SessionResponse sessionResp = attendanceService.openSession(
            event.getEventId(), new CreateSessionRequest(now.minusSeconds(60), now.plusSeconds(3600)), adminPrincipal
        );

        // Transition event to CLOSED
        eventService.transition(event.getEventId(), "close", adminPrincipal);

        // Attempting to complete the event must fail while an active session exists
        final Event eventToComplete = event;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            eventService.transition(eventToComplete.getEventId(), "complete", adminPrincipal)
        );
        assertTrue(ex.getMessage().contains("Cannot complete event while attendance sessions are still open"));

        // Close active session
        attendanceService.closeSession(sessionResp.sessionId(), adminPrincipal);

        // Now event completion succeeds
        EventResponse completed = eventService.transition(event.getEventId(), "complete", adminPrincipal);
        assertEquals("COMPLETED", completed.status());
    }
}
