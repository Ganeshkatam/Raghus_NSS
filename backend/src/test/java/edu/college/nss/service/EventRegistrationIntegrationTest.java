package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.exception.EventCapacityReachedException;
import edu.college.nss.exception.RegistrationClosedException;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.EventCreateRequest;
import edu.college.nss.web.dto.RegistrationRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EventRegistrationIntegrationTest {
    @Autowired EventService eventService;
    @Autowired EventRepository eventRepository;
    @Autowired EventRegistrationRepository registrationRepository;
    @Autowired AttendanceSessionRepository sessionRepository;
    @Autowired AttendanceRecordRepository recordRepository;
    @Autowired AttendanceCorrectionRepository correctionRepository;
    @Autowired ServiceHourEntryRepository serviceHourRepository;
    @Autowired NssUnitRepository unitRepository;
    @Autowired UserRepository userRepository;
    @Autowired VolunteerRepository volunteerRepository;
    @Autowired VolunteerStatusHistoryRepository statusHistoryRepository;
    @Autowired UnitTransferHistoryRepository transferHistoryRepository;
    @Autowired UnitMembershipRepository membershipRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AnnouncementRepository announcementRepository;

    private User admin;
    private NssUnit unit;
    private UserDetails adminPrincipal;

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

        admin = userRepository.save(new User("Admin", "admin-test@raghunss.edu", "x", null));
        unit = unitRepository.save(new NssUnit("Test Unit", "TEST-01", admin));
        adminPrincipal = new org.springframework.security.core.userdetails.User(
            admin.getEmail(), "x", List.of(new SimpleGrantedAuthority("ADMIN")));
    }

    @Test
    void lifecycle_shouldFollowPublishedOpenClosedCompletedPath() {
        EventCreateRequest request = new EventCreateRequest(
            unit.getUnitId(), "Tree Plantation", "Community activity", "SERVICE",
            Instant.now().plusSeconds(7200), Instant.now().plusSeconds(10800),
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7000),
            "College Campus", 20
        );

        var created = eventService.create(request, adminPrincipal);
        assertEquals("DRAFT", created.status());

        assertEquals("PUBLISHED", eventService.transition(created.eventId(), "publish", adminPrincipal).status());
        assertEquals("OPEN", eventService.transition(created.eventId(), "open", adminPrincipal).status());
        assertEquals("CLOSED", eventService.transition(created.eventId(), "close", adminPrincipal).status());
        assertEquals("COMPLETED", eventService.transition(created.eventId(), "complete", adminPrincipal).status());

        assertThrows(IllegalStateException.class,
            () -> eventService.transition(created.eventId(), "open", adminPrincipal));
    }

    @Test
    void registration_shouldRejectOutsideWindowAndAllowEligibleVolunteer() {
        Volunteer volunteer = createVolunteer("student1@raghunss.edu", "COL-001");
        membershipRepository.save(new UnitMembership(volunteer, unit));

        Instant start = Instant.now().plusSeconds(7200);
        EventCreateRequest request = new EventCreateRequest(
            unit.getUnitId(), "Blood Donation Awareness", null, "AWARENESS",
            start, start.plusSeconds(3600), Instant.now().plusSeconds(1800),
            Instant.now().plusSeconds(3600), "Seminar Hall", 5);

        var event = eventService.create(request, adminPrincipal);
        eventService.transition(event.eventId(), "publish", adminPrincipal);
        eventService.transition(event.eventId(), "open", adminPrincipal);

        UserDetails volunteerPrincipal = org.springframework.security.core.userdetails.User
            .withUsername(volunteer.getUser().getEmail()).password("x").roles("VOLUNTEER").build();

        assertThrows(RegistrationClosedException.class,
            () -> eventService.register(event.eventId(), new RegistrationRequest(volunteer.getVolunteerId()), volunteerPrincipal));
    }

    @Test
    void duplicateRegistration_shouldBeRejected() {
        Volunteer volunteer = createVolunteer("student2@raghunss.edu", "COL-002");
        membershipRepository.save(new UnitMembership(volunteer, unit));

        Instant now = Instant.now();
        Event event = new Event(unit, admin, "Workshop", null, "TRAINING",
            now.plusSeconds(3600), now.plusSeconds(7200), now.minusSeconds(60), now.plusSeconds(1800),
            "Lab", 5);
        event.setStatus("OPEN");
        Event savedEvent = eventRepository.save(event);

        UserDetails principal = org.springframework.security.core.userdetails.User
            .withUsername(volunteer.getUser().getEmail()).password("x").roles("VOLUNTEER").build();

        eventService.register(savedEvent.getEventId(), new RegistrationRequest(volunteer.getVolunteerId()), principal);
        assertThrows(edu.college.nss.exception.DuplicateRegistrationException.class,
            () -> eventService.register(savedEvent.getEventId(), new RegistrationRequest(volunteer.getVolunteerId()), principal));
    }

    @Test
    void concurrentLastSeat_shouldAllowExactlyOneRegistration() throws Exception {
        Volunteer first = createVolunteer("race1@raghunss.edu", "RACE-001");
        Volunteer second = createVolunteer("race2@raghunss.edu", "RACE-002");
        membershipRepository.save(new UnitMembership(first, unit));
        membershipRepository.save(new UnitMembership(second, unit));

        Instant now = Instant.now();
        Event event = new Event(unit, admin, "Capacity Race", null, "SERVICE",
            now.plusSeconds(3600), now.plusSeconds(7200), now.minusSeconds(60), now.plusSeconds(1800),
            "Auditorium", 1);
        event.setStatus("OPEN");
        event = eventRepository.save(event);

        Event finalEvent = event;
        UserDetails p1 = org.springframework.security.core.userdetails.User.withUsername(first.getUser().getEmail())
            .password("x").roles("VOLUNTEER").build();
        UserDetails p2 = org.springframework.security.core.userdetails.User.withUsername(second.getUser().getEmail())
            .password("x").roles("VOLUNTEER").build();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> attempt1 = () -> attemptRegistration(finalEvent.getEventId(), first.getVolunteerId(), p1, ready, start);
        Callable<Boolean> attempt2 = () -> attemptRegistration(finalEvent.getEventId(), second.getVolunteerId(), p2, ready, start);

        Future<Boolean> f1 = pool.submit(attempt1);
        Future<Boolean> f2 = pool.submit(attempt2);
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        boolean oneSucceeded = f1.get(10, TimeUnit.SECONDS);
        boolean twoSucceeded = f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertEquals(1, (oneSucceeded ? 1 : 0) + (twoSucceeded ? 1 : 0));
        assertEquals(1, registrationRepository.countRegistered(finalEvent.getEventId()));
    }

    private boolean attemptRegistration(UUID eventId, UUID volunteerId, UserDetails principal,
                                        CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            eventService.register(eventId, new RegistrationRequest(volunteerId), principal);
            return true;
        } catch (EventCapacityReachedException ex) {
            return false;
        } catch (Exception ex) {
            if (ex.getCause() instanceof EventCapacityReachedException) return false;
            throw new RuntimeException(ex);
        }
    }

    private Volunteer createVolunteer(String email, String collegeId) {
        User user = userRepository.save(new User(email.split("@")[0], email, "x", null));
        return volunteerRepository.save(new Volunteer(user, collegeId, "CSE", 2));
    }
}
