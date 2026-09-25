package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.*;
import edu.college.nss.web.dto.ServiceHourDTOs.ServiceHourClaimRequest;
import edu.college.nss.web.dto.ServiceHourDTOs.ServiceHourResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MultiUnitEventIntegrationTest {

    @Autowired private EventService eventService;
    @Autowired private ServiceHourService serviceHourService;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventRegistrationRepository registrationRepository;
    @Autowired private ServiceHourEntryRepository serviceHourRepository;
    @Autowired private NssUnitRepository unitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VolunteerRepository volunteerRepository;
    @Autowired private UnitMembershipRepository membershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AttendanceSessionRepository sessionRepository;
    @Autowired private AttendanceRecordRepository recordRepository;
    @Autowired private AttendanceCorrectionRepository correctionRepository;
    @Autowired private VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired private UnitTransferHistoryRepository transferHistoryRepository;
    @Autowired private AnnouncementRepository announcementRepository;

    private User admin;
    private User po1;
    private User po2;
    private User po3;
    private NssUnit unit1;
    private NssUnit unit2;
    private NssUnit unit3;

    private UserDetails adminPrincipal;
    private UserDetails po1Principal;
    private UserDetails po2Principal;
    private UserDetails po3Principal;

    private Volunteer v1;
    private Volunteer v2;
    private Volunteer v3;
    private UserDetails v1Principal;
    private UserDetails v2Principal;
    private UserDetails v3Principal;

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

        admin = userRepository.save(new User("Admin User", "admin-multi@raghunss.edu", "pass", "9000000001"));
        po1 = userRepository.save(new User("PO Unit 1", "po1-multi@raghunss.edu", "pass", "9000000002"));
        po2 = userRepository.save(new User("PO Unit 2", "po2-multi@raghunss.edu", "pass", "9000000003"));
        po3 = userRepository.save(new User("PO Unit 3", "po3-multi@raghunss.edu", "pass", "9000000004"));

        unit1 = unitRepository.save(new NssUnit("Unit 1", "UNIT-01", po1));
        unit2 = unitRepository.save(new NssUnit("Unit 2", "UNIT-02", po2));
        unit3 = unitRepository.save(new NssUnit("Unit 3", "UNIT-03", po3));

        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        po1Principal = new org.springframework.security.core.userdetails.User(
            po1.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_PROGRAMME_OFFICER")));
        po2Principal = new org.springframework.security.core.userdetails.User(
            po2.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_PROGRAMME_OFFICER")));
        po3Principal = new org.springframework.security.core.userdetails.User(
            po3.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_PROGRAMME_OFFICER")));

        User vu1 = userRepository.save(new User("Vol One", "vol1-multi@raghunss.edu", "pass", "9100000001"));
        User vu2 = userRepository.save(new User("Vol Two", "vol2-multi@raghunss.edu", "pass", "9100000002"));
        User vu3 = userRepository.save(new User("Vol Three", "vol3-multi@raghunss.edu", "pass", "9100000003"));

        v1 = volunteerRepository.save(new Volunteer(vu1, "REC-V1", "CSE", 2));
        v2 = volunteerRepository.save(new Volunteer(vu2, "REC-V2", "ECE", 2));
        v3 = volunteerRepository.save(new Volunteer(vu3, "REC-V3", "MECH", 2));

        membershipRepository.save(new UnitMembership(v1, unit1));
        membershipRepository.save(new UnitMembership(v2, unit2));
        membershipRepository.save(new UnitMembership(v3, unit3));

        v1Principal = new org.springframework.security.core.userdetails.User(
            vu1.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VOLUNTEER")));
        v2Principal = new org.springframework.security.core.userdetails.User(
            vu2.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VOLUNTEER")));
        v3Principal = new org.springframework.security.core.userdetails.User(
            vu3.getEmail(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VOLUNTEER")));
    }

    @Test
    void singleUnitEvent_allowsOnlyOrganizingUnitVolunteerToRegister() {
        EventCreateRequest req = new EventCreateRequest(
            unit1.getUnitId(), unit1.getUnitId(), "UNIT", List.of(unit1.getUnitId()), List.of(unit1.getUnitId()),
            "Single Unit Camp", "Testing single unit", "SERVICE",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(3500),
            "Campus Grounds", 50
        );

        EventResponse created = eventService.create(req, po1Principal);
        assertEquals("UNIT", created.eventScope());
        assertEquals(unit1.getUnitId(), created.organizingUnitId());
        assertEquals(1, created.participatingUnits().size());

        eventService.transition(created.eventId(), "publish", po1Principal);
        eventService.transition(created.eventId(), "open", po1Principal);

        // Vol 1 (Unit 1) registers successfully
        EventRegistrationResponse reg1 = eventService.register(created.eventId(), new RegistrationRequest(v1.getVolunteerId()), v1Principal);
        assertEquals("CONFIRMED", reg1.status());

        // Vol 2 (Unit 2) registration is denied
        assertThrows(AccessDeniedException.class, () ->
            eventService.register(created.eventId(), new RegistrationRequest(v2.getVolunteerId()), v2Principal));
    }

    @Test
    void multiUnitEvent_allowsVolunteersFromAllParticipatingUnitsToRegister() {
        EventCreateRequest req = new EventCreateRequest(
            unit1.getUnitId(), unit1.getUnitId(), "MULTI_UNIT",
            List.of(unit1.getUnitId(), unit2.getUnitId()), List.of(unit1.getUnitId(), unit2.getUnitId()),
            "Joint Mega Blood Donation", "Organized by Unit 1 with Unit 2", "BLOOD_DONATION",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(3500),
            "College Auditorium", 100
        );

        EventResponse created = eventService.create(req, po1Principal);
        assertEquals("MULTI_UNIT", created.eventScope());
        assertEquals(unit1.getUnitId(), created.organizingUnitId());
        assertEquals(2, created.participatingUnits().size());

        eventService.transition(created.eventId(), "publish", po1Principal);
        eventService.transition(created.eventId(), "open", po1Principal);

        // Unit 1 volunteer registers
        EventRegistrationResponse reg1 = eventService.register(created.eventId(), new RegistrationRequest(v1.getVolunteerId()), v1Principal);
        assertEquals("CONFIRMED", reg1.status());

        // Unit 2 volunteer registers
        EventRegistrationResponse reg2 = eventService.register(created.eventId(), new RegistrationRequest(v2.getVolunteerId()), v2Principal);
        assertEquals("CONFIRMED", reg2.status());

        // Unit 3 volunteer is denied
        assertThrows(AccessDeniedException.class, () ->
            eventService.register(created.eventId(), new RegistrationRequest(v3.getVolunteerId()), v3Principal));
    }

    @Test
    void collegeWideEvent_allowsVolunteersFromAllActiveUnitsToRegister() {
        EventCreateRequest req = new EventCreateRequest(
            unit1.getUnitId(), unit1.getUnitId(), "COLLEGE_WIDE",
            List.of(), List.of(),
            "Annual NSS Youth Day Festival", "Campus-wide festival", "CAMPUS_DRIVE",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(3500),
            "Main Stadium", 500
        );

        EventResponse created = eventService.create(req, po1Principal);
        assertEquals("COLLEGE_WIDE", created.eventScope());
        assertEquals(3, created.participatingUnits().size());

        eventService.transition(created.eventId(), "publish", po1Principal);
        eventService.transition(created.eventId(), "open", po1Principal);

        // All volunteers from units 1, 2, and 3 can register
        assertNotNull(eventService.register(created.eventId(), new RegistrationRequest(v1.getVolunteerId()), v1Principal));
        assertNotNull(eventService.register(created.eventId(), new RegistrationRequest(v2.getVolunteerId()), v2Principal));
        assertNotNull(eventService.register(created.eventId(), new RegistrationRequest(v3.getVolunteerId()), v3Principal));
    }

    @Test
    void multiUnitEvent_serviceHours_allowsClaimOnlyForParticipatingUnits() {
        Event event = new Event(
            unit1, admin, "Past Multi-Unit Cleanliness Drive", "Description", "CAMPUS_DRIVE",
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(14400), Instant.now().minusSeconds(7300),
            "Campus Gate", 50, "MULTI_UNIT", new java.util.HashSet<>(List.of(unit1, unit2))
        );
        event.publish();
        event.open();
        event.close();
        event.complete();
        event = eventRepository.save(event);

        // Vol 2 (participating Unit 2) can claim hours
        ServiceHourClaimRequest claimReq2 = new ServiceHourClaimRequest(
            BigDecimal.valueOf(3.0), event.getEventId(),
            "Attended multi-unit campus cleanup", "CAMPUS_DRIVE", "Photo verified", LocalDate.now()
        );
        ServiceHourResponse claimResp2 = serviceHourService.submitClaim(claimReq2, v2Principal);
        assertNotNull(claimResp2);
        assertEquals("PENDING", claimResp2.status());

        // Vol 3 (non-participating Unit 3) cannot claim hours
        ServiceHourClaimRequest claimReq3 = new ServiceHourClaimRequest(
            BigDecimal.valueOf(3.0), event.getEventId(),
            "Claiming hours for non-participating event", "CAMPUS_DRIVE", "None", LocalDate.now()
        );
        assertThrows(AccessDeniedException.class, () ->
            serviceHourService.submitClaim(claimReq3, v3Principal));
    }

    @Test
    void multiUnitEvent_governance_onlyOrganizingPoCanManageEvent() {
        EventCreateRequest req = new EventCreateRequest(
            unit1.getUnitId(), unit1.getUnitId(), "MULTI_UNIT",
            List.of(unit1.getUnitId(), unit2.getUnitId()), List.of(unit1.getUnitId(), unit2.getUnitId()),
            "Governance Verification Event", "Checking permissions", "SERVICE",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(3500),
            "Seminar Hall", 40
        );

        EventResponse created = eventService.create(req, po1Principal);

        // PO 2 (participating unit) CANNOT transition or cancel organizing Unit 1's event
        assertThrows(AccessDeniedException.class, () ->
            eventService.transition(created.eventId(), "publish", po2Principal));
        assertThrows(AccessDeniedException.class, () ->
            eventService.transition(created.eventId(), "cancel", po2Principal));

        // PO 1 (organizing unit) CAN publish
        EventResponse published = eventService.transition(created.eventId(), "publish", po1Principal);
        assertEquals("PUBLISHED", published.status());
    }

    @Test
    void multiUnitEvent_visibility_publishedIsVisibleToParticipatingPoSearch() {
        EventCreateRequest req = new EventCreateRequest(
            unit1.getUnitId(), unit1.getUnitId(), "MULTI_UNIT",
            List.of(unit1.getUnitId(), unit2.getUnitId()), List.of(unit1.getUnitId(), unit2.getUnitId()),
            "Search Visibility Test", "Checking visibility", "SERVICE",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(3500),
            "Seminar Hall", 40
        );

        EventResponse created = eventService.create(req, po1Principal);

        // While in DRAFT: PO 2 search for Unit 2 does NOT return Unit 1's draft
        Page<EventResponse> po2DraftResults = eventService.search(unit2.getUnitId(), "DRAFT", PageRequest.of(0, 10), po2Principal);
        assertTrue(po2DraftResults.getContent().stream().noneMatch(e -> e.eventId().equals(created.eventId())));

        // Publish event
        eventService.transition(created.eventId(), "publish", po1Principal);

        // When PUBLISHED: PO 2 search for Unit 2 returns the event because Unit 2 participates
        Page<EventResponse> po2PublishedResults = eventService.search(unit2.getUnitId(), "PUBLISHED", PageRequest.of(0, 10), po2Principal);
        assertTrue(po2PublishedResults.getContent().stream().anyMatch(e -> e.eventId().equals(created.eventId())));

        // PO for Unit 3 does not see this event when searching Unit 3
        Page<EventResponse> po3Results = eventService.search(unit3.getUnitId(), "PUBLISHED", PageRequest.of(0, 10), po3Principal);
        assertTrue(po3Results.getContent().stream().noneMatch(e -> e.eventId().equals(created.eventId())));
    }

    @Test
    void multiUnitEvent_update_enforcesAtLeastTwoParticipatingUnits() {
        // 1. Create standard single UNIT event
        EventCreateRequest req = new EventCreateRequest(
            unit1.getUnitId(), unit1.getUnitId(), "UNIT",
            null, null,
            "Scope Upgrade Test", "Upgrading scope", "SERVICE",
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(3500),
            "Campus Ground", 30
        );
        EventResponse created = eventService.create(req, po1Principal);
        assertEquals("UNIT", created.eventScope());
        assertEquals(1, created.participatingUnits().size());

        // 2. Attempt to update scope to MULTI_UNIT without participating units -> throws IllegalArgumentException
        EventUpdateRequest invalidUpdate1 = new EventUpdateRequest(
            null, null, null, null, null, null, null, null, null,
            "MULTI_UNIT", null, null
        );
        assertThrows(IllegalArgumentException.class, () ->
            eventService.update(created.eventId(), invalidUpdate1, po1Principal));

        // 3. Attempt to update scope to MULTI_UNIT with only organizing unit -> throws IllegalArgumentException
        EventUpdateRequest invalidUpdate2 = new EventUpdateRequest(
            null, null, null, null, null, null, null, null, null,
            "MULTI_UNIT", List.of(unit1.getUnitId()), List.of(unit1.getUnitId())
        );
        assertThrows(IllegalArgumentException.class, () ->
            eventService.update(created.eventId(), invalidUpdate2, po1Principal));

        // 4. Update scope to MULTI_UNIT with at least 2 distinct units -> succeeds
        EventUpdateRequest validUpdate = new EventUpdateRequest(
            null, null, null, null, null, null, null, null, null,
            "MULTI_UNIT", List.of(unit1.getUnitId(), unit2.getUnitId()), List.of(unit1.getUnitId(), unit2.getUnitId())
        );
        EventResponse updated = eventService.update(created.eventId(), validUpdate, po1Principal);
        assertEquals("MULTI_UNIT", updated.eventScope());
        assertEquals(2, updated.participatingUnits().size());

        // 5. Attempt to update existing MULTI_UNIT event to reduce participating units to 1 -> throws IllegalArgumentException
        EventUpdateRequest invalidReduction = new EventUpdateRequest(
            null, null, null, null, null, null, null, null, null,
            null, List.of(unit1.getUnitId()), List.of(unit1.getUnitId())
        );
        assertThrows(IllegalArgumentException.class, () ->
            eventService.update(created.eventId(), invalidReduction, po1Principal));
    }
}
