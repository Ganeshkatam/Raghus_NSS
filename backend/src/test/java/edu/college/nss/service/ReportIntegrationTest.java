package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.ReportDTOs.InstitutionalMetricsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportIntegrationTest {

    @Autowired ReportService reportService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AnnouncementRepository announcementRepository;
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

    private User admin;
    private NssUnit unit;
    private Volunteer volunteer;
    private Event event;

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
        volunteerRepository.deleteAll();
        unitRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Dr. Principal", "principal@raghunss.edu", "pass", null));
        unit = unitRepository.save(new NssUnit("Unit 1", "REC-01", admin));

        User volUser = userRepository.save(new User("Surya Rao", "surya@raghunss.edu", "pass", null));
        volunteer = volunteerRepository.save(new Volunteer(volUser, "21B91A0504", "IT", 2));
        membershipRepository.save(new UnitMembership(volunteer, unit));

        Instant now = Instant.now();
        event = new Event(unit, admin, "Pulse Polio Drive", "Immunization initiative", "SERVICE",
            now.minusSeconds(7200), now.minusSeconds(3600), now.minusSeconds(10800), now.minusSeconds(7200),
            "Community Health Centre", 30);
        event.setStatus("COMPLETED");
        event = eventRepository.save(event);

        ServiceHourEntry entry = new ServiceHourEntry(
            volunteer, event, null, BigDecimal.valueOf(3.50), "APPROVED", admin, "Polio immunization drive assistance"
        );
        serviceHourRepository.save(entry);
    }

    @Test
    void getInstitutionalMetrics_shouldAggregateAccurateCountsAndHours() {
        InstitutionalMetricsResponse metrics = reportService.getInstitutionalMetrics();

        assertEquals(1, metrics.totalVolunteers());
        assertEquals(1, metrics.activeVolunteers());
        assertEquals(1, metrics.totalUnits());
        assertEquals(1, metrics.totalEvents());
        assertEquals(1, metrics.completedEvents());
        assertEquals(3.5, metrics.totalServiceHours().doubleValue(), 0.001);
        assertEquals(1, metrics.unitPerformance().size());
        assertEquals("Unit 1", metrics.unitPerformance().get(0).unitName());
        assertEquals(1, metrics.unitPerformance().get(0).volunteerCount());
        assertEquals(1, metrics.unitPerformance().get(0).eventCount());
    }

    @Test
    void exportVolunteersCsv_shouldContainHeaderAndVolunteerData() {
        byte[] csvBytes = reportService.exportVolunteersCsv();
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Volunteer ID,Full Name,College ID,Department,Year of Study,Status,Enrollment Unit,Email"));
        assertTrue(csv.contains("Surya Rao"));
        assertTrue(csv.contains("21B91A0504"));
        assertTrue(csv.contains("Unit 1"));
    }

    @Test
    void exportEventsCsv_shouldContainHeaderAndEventData() {
        byte[] csvBytes = reportService.exportEventsCsv();
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Event ID,Title,Event Type,NSS Unit,Start Time,End Time,Venue,Capacity,Status,Registered Count"));
        assertTrue(csv.contains("Pulse Polio Drive"));
        assertTrue(csv.contains("COMPLETED"));
    }

    @Test
    void exportServiceHoursCsv_shouldContainApprovedHours() {
        byte[] csvBytes = reportService.exportServiceHoursCsv();
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Entry ID,Volunteer Name,College ID,Activity / Event,Hours,Status,Approved By,Created At"));
        assertTrue(csv.contains("Surya Rao"));
        assertTrue(csv.contains("3.50"));
        assertTrue(csv.contains("Pulse Polio Drive"));
    }
}
