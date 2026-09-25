package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.*;
import edu.college.nss.web.dto.AttendanceDTOs.*;
import edu.college.nss.web.dto.ServiceHourDTOs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ProgrammeOfficerNegativeAuthorizationIntegrationTest {

    @Autowired private VolunteerService volunteerService;
    @Autowired private EventService eventService;
    @Autowired private ReportService reportService;
    @Autowired private ServiceHourService serviceHourService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private NssUnitService unitService;

    @Autowired private UserRepository userRepository;
    @Autowired private NssUnitRepository unitRepository;
    @Autowired private VolunteerRepository volunteerRepository;
    @Autowired private UnitMembershipRepository membershipRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ServiceHourEntryRepository serviceHourRepository;
    @Autowired private AttendanceSessionRepository sessionRepository;
    @Autowired private AttendanceRecordRepository recordRepository;
    @Autowired private AttendanceCorrectionRepository correctionRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EventRegistrationRepository registrationRepository;
    @Autowired private VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired private UnitTransferHistoryRepository transferHistoryRepository;

    private User admin;
    private User po1;
    private User po2;
    private User poUnassigned;

    private NssUnit unit1;
    private NssUnit unit2;
    private NssUnit unit3;

    private Volunteer volUnit1;
    private Volunteer volUnit2;
    private Volunteer volUnassigned;

    private Event eventUnit2;

    private UserDetails po1Principal;
    private UserDetails po2Principal;
    private UserDetails poUnassignedPrincipal;

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

        admin = userRepository.save(new User("Admin", "admin@raghunss.edu", "pass", null));
        po1 = userRepository.save(new User("PO Unit 1", "po1@raghunss.edu", "pass", null));
        po2 = userRepository.save(new User("PO Unit 2", "po2@raghunss.edu", "pass", null));
        poUnassigned = userRepository.save(new User("PO Unassigned", "pounassigned@raghunss.edu", "pass", null));

        po1Principal = new org.springframework.security.core.userdetails.User(
            po1.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));
        po2Principal = new org.springframework.security.core.userdetails.User(
            po2.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));
        poUnassignedPrincipal = new org.springframework.security.core.userdetails.User(
            poUnassigned.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));

        unit1 = unitRepository.save(new NssUnit("NSS Unit 1", "UNIT-01", po1, 100));
        unit2 = unitRepository.save(new NssUnit("NSS Unit 2", "UNIT-02", po2, 100));
        unit3 = unitRepository.save(new NssUnit("NSS Unit 3", "UNIT-03", null, 100));

        User userVol1 = userRepository.save(new User("Volunteer One", "vol1@raghunss.edu", "pass", null));
        volUnit1 = volunteerRepository.save(new Volunteer(userVol1, "21B91A0101", "CSE", 3));
        membershipRepository.save(new UnitMembership(volUnit1, unit1));

        User userVol2 = userRepository.save(new User("Volunteer Two", "vol2@raghunss.edu", "pass", null));
        volUnit2 = volunteerRepository.save(new Volunteer(userVol2, "21B91A0201", "ECE", 3));
        membershipRepository.save(new UnitMembership(volUnit2, unit2));

        User userVolUnassigned = userRepository.save(new User("Volunteer Three", "vol3@raghunss.edu", "pass", null));
        volUnassigned = volunteerRepository.save(new Volunteer(userVolUnassigned, "21B91A0301", "MECH", 2));

        Instant start = Instant.now().plusSeconds(3600);
        Instant end = Instant.now().plusSeconds(7200);
        eventUnit2 = new Event(unit2, po2, "Unit 2 Community Drive", "Community Service", "SERVICE",
            start, end, start.minusSeconds(1800), start, "Beach Road", 50);
        eventRepository.save(eventUnit2);
    }

    @Test
    void po_volunteerList_filtersOutOtherUnitVolunteers() {
        Page<VolunteerResponse> results = volunteerService.searchVolunteers(null, null, null, PageRequest.of(0, 10), po1Principal);

        assertEquals(1, results.getTotalElements());
        assertEquals(volUnit1.getVolunteerId(), results.getContent().get(0).volunteerId());
        assertTrue(results.getContent().stream().noneMatch(v -> v.volunteerId().equals(volUnit2.getVolunteerId())));
    }

    @Test
    void po_volunteerDetail_deniesDirectAccessToOtherUnitVolunteer() {
        assertThrows(AccessDeniedException.class, () ->
            volunteerService.getVolunteerById(volUnit2.getVolunteerId(), po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            volunteerService.getVolunteerMemberships(volUnit2.getVolunteerId(), po1Principal));
    }

    @Test
    void po_volunteerStatusChange_deniesModificationOfOtherUnitVolunteer() {
        assertThrows(AccessDeniedException.class, () ->
            volunteerService.updateVolunteerStatus(
                volUnit2.getVolunteerId(),
                new VolunteerStatusUpdateRequest("INACTIVE", "Unauthorized change"),
                po1Principal
            ));

        assertThrows(AccessDeniedException.class, () ->
            volunteerService.updateVolunteer(
                volUnit2.getVolunteerId(),
                new VolunteerUpdateRequest("Hacked Name", "9999999999", "IT", 4, "INACTIVE"),
                po1Principal
            ));
    }

    @Test
    void po_eventAccess_deniesDraftEventOfOtherUnit() {
        assertThrows(AccessDeniedException.class, () ->
            eventService.get(eventUnit2.getEventId(), po1Principal));
    }

    @Test
    void po_eventModification_deniesUpdatingOtherUnitEvent() {
        EventUpdateRequest updateReq = new EventUpdateRequest(
            "Hacked Title", "Hacked Desc", "ORIENTATION",
            Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800),
            null, null, "Auditorium", 100
        );

        assertThrows(AccessDeniedException.class, () ->
            eventService.update(eventUnit2.getEventId(), updateReq, po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            eventService.transition(eventUnit2.getEventId(), "publish", po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            eventService.transition(eventUnit2.getEventId(), "cancel", po1Principal));
    }

    @Test
    void po_csvExport_deniesExportOfOtherUnit() {
        assertThrows(AccessDeniedException.class, () ->
            reportService.exportVolunteersCsv(unit2.getUnitId(), null, po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            reportService.exportEventsCsv(unit2.getUnitId(), null, null, null, po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            reportService.exportServiceHoursCsv(unit2.getUnitId(), null, null, po1Principal));
    }

    @Test
    void po_csvExport_unscopedFiltersToAssignedUnitOnly() {
        byte[] csvBytes = reportService.exportVolunteersCsv(null, null, po1Principal);
        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("21B91A0101"), "CSV must include Unit 1 volunteer");
        assertFalse(csvContent.contains("21B91A0201"), "CSV must NOT include Unit 2 volunteer");
    }

    @Test
    void po_attendanceCorrection_filtersQueueAndDeniesReviewOfOtherUnit() {
        Event openEventUnit2 = new Event(unit2, po2, "Open Unit 2 Drive", "Drive", "SERVICE",
            Instant.now().minusSeconds(1800), Instant.now().plusSeconds(1800), null, null, "Campus", 50);
        openEventUnit2.publish();
        openEventUnit2.open();
        openEventUnit2 = eventRepository.save(openEventUnit2);

        AttendanceSession session = sessionRepository.save(new AttendanceSession(
            openEventUnit2, po2, Instant.now().minusSeconds(600), Instant.now().plusSeconds(600), "secret"
        ));
        AttendanceRecord record = recordRepository.save(new AttendanceRecord(session, volUnit2, "MANUAL_COORDINATOR", "PRESENT"));
        AttendanceCorrection correction = correctionRepository.save(new AttendanceCorrection(
            record, po2, "PRESENT", "EXCUSED", "Medical document verified"
        ));

        // PO1 cannot see Unit 2 correction in pending queue
        List<CorrectionResponse> po1Pending = attendanceService.getPendingCorrections(po1Principal);
        assertTrue(po1Pending.stream().noneMatch(c -> c.correctionId().equals(correction.getCorrectionId())));

        // PO1 cannot review Unit 2 correction
        assertThrows(AccessDeniedException.class, () ->
            attendanceService.reviewCorrection(
                correction.getCorrectionId(),
                new edu.college.nss.web.dto.CorrectionReviewRequest(true, "PO1 approving unauthorized"),
                po1Principal
            ));
    }

    @Test
    void po_serviceHourClaims_filtersQueueAndDeniesReviewOfOtherUnit() {
        ServiceHourEntry savedClaim = serviceHourRepository.save(new ServiceHourEntry(
            volUnit2, null, null, BigDecimal.valueOf(5), "PENDING", null, "Tree Plantation"
        ));
        final UUID claimId = savedClaim.getEntryId();

        // PO1 cannot see Unit 2 claim in pending queue
        List<ServiceHourResponse> po1Pending = serviceHourService.getPendingClaims(po1Principal);
        assertTrue(po1Pending.stream().noneMatch(c -> c.entryId().equals(claimId)));

        // PO1 cannot approve or reject Unit 2 claim
        assertThrows(AccessDeniedException.class, () ->
            serviceHourService.reviewClaim(claimId, new ServiceHourReviewRequest("APPROVE", null), po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            serviceHourService.reviewClaim(claimId, new ServiceHourReviewRequest("REJECT", "Denied by PO1"), po1Principal));
    }

    @Test
    void po_unitAdministration_deniesUpdatingOtherUnit() {
        assertThrows(AccessDeniedException.class, () ->
            unitService.updateUnit(unit2.getUnitId(), new UnitUpdateRequest("Unit 2 Hacked", null, false, 200), po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            unitService.addMemberToUnit(unit2.getUnitId(), volUnassigned.getVolunteerId(), po1Principal));

        assertThrows(AccessDeniedException.class, () ->
            unitService.transferVolunteer(
                unit2.getUnitId(),
                new UnitTransferRequest(volUnit2.getVolunteerId(), unit3.getUnitId(), "Unauthorized transfer attempt"),
                po1Principal
            ));
    }

    @Test
    void po_unassignedOfficer_cannotAccessInstitutionWideData() {
        Page<VolunteerResponse> volunteers = volunteerService.searchVolunteers(null, null, null, PageRequest.of(0, 10), poUnassignedPrincipal);
        assertEquals(0, volunteers.getTotalElements(), "Unassigned PO must receive empty volunteer search results");

        List<ServiceHourResponse> claims = serviceHourService.getPendingClaims(poUnassignedPrincipal);
        assertTrue(claims.isEmpty(), "Unassigned PO must receive empty pending claims");

        List<CorrectionResponse> corrections = attendanceService.getPendingCorrections(poUnassignedPrincipal);
        assertTrue(corrections.isEmpty(), "Unassigned PO must receive empty pending corrections");

        byte[] volunteerCsv = reportService.exportVolunteersCsv(null, null, poUnassignedPrincipal);
        assertEquals(0, volunteerCsv.length, "Unassigned PO must receive empty volunteer CSV");

        byte[] eventCsv = reportService.exportEventsCsv(null, null, null, null, poUnassignedPrincipal);
        assertEquals(0, eventCsv.length, "Unassigned PO must receive empty event CSV");

        byte[] hoursCsv = reportService.exportServiceHoursCsv(null, null, null, poUnassignedPrincipal);
        assertEquals(0, hoursCsv.length, "Unassigned PO must receive empty service hours CSV");
    }
}
