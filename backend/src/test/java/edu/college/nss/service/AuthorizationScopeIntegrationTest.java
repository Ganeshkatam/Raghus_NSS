package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.*;
import edu.college.nss.web.dto.AnnouncementDTOs.*;
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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AuthorizationScopeIntegrationTest {

    @Autowired VolunteerService volunteerService;
    @Autowired EventService eventService;
    @Autowired ReportService reportService;
    @Autowired ServiceHourService serviceHourService;
    @Autowired AttendanceService attendanceService;
    @Autowired AnnouncementService announcementService;
    @Autowired NssUnitService unitService;

    @Autowired UserRepository userRepository;
    @Autowired NssUnitRepository unitRepository;
    @Autowired VolunteerRepository volunteerRepository;
    @Autowired UnitMembershipRepository membershipRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ServiceHourEntryRepository serviceHourRepository;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired AttendanceRecordRepository recordRepository;
    @Autowired AttendanceCorrectionRepository correctionRepository;
    @Autowired AnnouncementRepository announcementRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired EventRegistrationRepository registrationRepository;
    @Autowired VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired UnitTransferHistoryRepository transferHistoryRepository;

    private User admin;
    private User po1;
    private User po2;
    private NssUnit unit1;
    private NssUnit unit2;
    private Volunteer volUnit1;
    private Volunteer volUnit2;

    private UserDetails adminPrincipal;
    private UserDetails po1Principal;
    private UserDetails po2Principal;

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

        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ADMIN")));
        po1Principal = new org.springframework.security.core.userdetails.User(
            po1.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));
        po2Principal = new org.springframework.security.core.userdetails.User(
            po2.getEmail(), "pass", List.of(new SimpleGrantedAuthority("PROGRAMME_OFFICER")));

        unit1 = unitRepository.save(new NssUnit("NSS Unit 1", "UNIT-01", po1, 100));
        unit2 = unitRepository.save(new NssUnit("NSS Unit 2", "UNIT-02", po2, 100));

        User userVol1 = userRepository.save(new User("Volunteer One", "vol1@raghunss.edu", "pass", null));
        volUnit1 = volunteerRepository.save(new Volunteer(userVol1, "21B91A0101", "CSE", 3));
        membershipRepository.save(new UnitMembership(volUnit1, unit1));

        User userVol2 = userRepository.save(new User("Volunteer Two", "vol2@raghunss.edu", "pass", null));
        volUnit2 = volunteerRepository.save(new Volunteer(userVol2, "21B91A0201", "ECE", 3));
        membershipRepository.save(new UnitMembership(volUnit2, unit2));
    }

    @Test
    void volunteerSearch_scopedToProgrammeOfficerUnit() {
        // PO1 searches volunteers -> should ONLY receive volunteers belonging to Unit 1
        Page<VolunteerResponse> po1Results = volunteerService.searchVolunteers(null, null, null, PageRequest.of(0, 10), po1Principal);
        assertEquals(1, po1Results.getTotalElements());
        assertEquals("21B91A0101", po1Results.getContent().get(0).collegeId());

        // PO2 searches volunteers -> should ONLY receive volunteers belonging to Unit 2
        Page<VolunteerResponse> po2Results = volunteerService.searchVolunteers(null, null, null, PageRequest.of(0, 10), po2Principal);
        assertEquals(1, po2Results.getTotalElements());
        assertEquals("21B91A0201", po2Results.getContent().get(0).collegeId());

        // Admin searches volunteers -> receives institution-wide results
        Page<VolunteerResponse> adminResults = volunteerService.searchVolunteers(null, null, null, PageRequest.of(0, 10), adminPrincipal);
        assertEquals(2, adminResults.getTotalElements());
    }

    @Test
    void eventSearch_programmeOfficerCannotSeeDraftEventsOfOtherUnits() {
        // Create draft event in Unit 2
        Event draftUnit2 = new Event(unit2, po2, "Secret Unit 2 Event", "Desc", "WORKSHOP",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), null, null, "Seminar Hall", 50);
        eventRepository.save(draftUnit2);

        // PO 1 searches Unit 2 events -> cannot see draft events of Unit 2
        Page<EventResponse> po1SearchUnit2 = eventService.search(unit2.getUnitId(), "DRAFT", PageRequest.of(0, 10), po1Principal);
        assertEquals(0, po1SearchUnit2.getTotalElements());

        // PO 1 tries to GET the draft event of Unit 2 directly -> denied
        assertThrows(AccessDeniedException.class, () -> eventService.get(draftUnit2.getEventId(), po1Principal));

        // PO 2 can see their own unit's draft event
        EventResponse po2Get = eventService.get(draftUnit2.getEventId(), po2Principal);
        assertNotNull(po2Get);
        assertEquals("Secret Unit 2 Event", po2Get.title());
    }

    @Test
    void pendingServiceHourClaims_scopedToProgrammeOfficerUnit() {
        ServiceHourEntry claim1 = new ServiceHourEntry(volUnit1, null, null, BigDecimal.valueOf(3), "PENDING", null, "Unit 1 Cleanliness");
        ServiceHourEntry claim2 = new ServiceHourEntry(volUnit2, null, null, BigDecimal.valueOf(4), "PENDING", null, "Unit 2 Plantation");
        serviceHourRepository.saveAll(List.of(claim1, claim2));

        // PO1 only sees claim from Unit 1 volunteer
        List<ServiceHourResponse> po1Claims = serviceHourService.getPendingClaims(po1Principal);
        assertEquals(1, po1Claims.size());
        assertEquals(volUnit1.getVolunteerId(), po1Claims.get(0).volunteerId());

        // PO1 cannot approve claim belonging to Unit 2 volunteer
        assertThrows(AccessDeniedException.class, () ->
            serviceHourService.reviewClaim(claim2.getEntryId(), new ServiceHourReviewRequest("APPROVE", null), po1Principal));

        // PO2 can approve claim 2
        ServiceHourResponse reviewed = serviceHourService.reviewClaim(claim2.getEntryId(), new ServiceHourReviewRequest("APPROVE", null), po2Principal);
        assertEquals("APPROVED", reviewed.status());
    }

    @Test
    void announcementPublishing_programmeOfficerCannotPublishToOtherUnitOrCollegeWide() {
        // PO1 cannot publish to Unit 2
        CreateAnnouncementRequest reqUnit2 = new CreateAnnouncementRequest("Unit 2 Meeting", "Content", unit2.getUnitId(), "NORMAL", null);
        assertThrows(AccessDeniedException.class, () -> announcementService.createAnnouncement(reqUnit2, po1Principal));

        // PO1 cannot publish college-wide (unitId == null)
        CreateAnnouncementRequest reqCollege = new CreateAnnouncementRequest("All College", "Content", null, "NORMAL", null);
        assertThrows(AccessDeniedException.class, () -> announcementService.createAnnouncement(reqCollege, po1Principal));

        // PO1 can publish to Unit 1
        CreateAnnouncementRequest reqUnit1 = new CreateAnnouncementRequest("Unit 1 Drive", "Content", unit1.getUnitId(), "NORMAL", null);
        AnnouncementResponse created = announcementService.createAnnouncement(reqUnit1, po1Principal);
        assertNotNull(created);
        assertEquals("Unit 1 Drive", created.title());
    }

    @Test
    void unitManagement_programmeOfficerCannotReassignOfficers() {
        UnitUpdateRequest req = new UnitUpdateRequest("Unit 1 Renamed", admin.getUserId(), false, 120);
        // PO cannot reassign unit officer
        assertThrows(AccessDeniedException.class, () -> unitService.updateUnit(unit1.getUnitId(), req, po1Principal));

        // PO cannot update other units
        UnitUpdateRequest req2 = new UnitUpdateRequest("Unit 2 Hack", null, false, 120);
        assertThrows(AccessDeniedException.class, () -> unitService.updateUnit(unit2.getUnitId(), req2, po1Principal));

        // PO can update name and capacity of their own unit
        UnitUpdateRequest reqValid = new UnitUpdateRequest("Unit 1 Updated", null, false, 150);
        UnitResponse updated = unitService.updateUnit(unit1.getUnitId(), reqValid, po1Principal);
        assertEquals("Unit 1 Updated", updated.unitName());
        assertEquals(150, updated.capacity());
    }
}
