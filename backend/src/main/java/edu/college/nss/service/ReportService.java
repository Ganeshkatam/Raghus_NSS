package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.ReportDTOs.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {

    private final VolunteerRepository volunteerRepository;
    private final NssUnitRepository unitRepository;
    private final EventRepository eventRepository;
    private final ServiceHourEntryRepository serviceHourRepository;
    private final UnitMembershipRepository membershipRepository;
    private final EventRegistrationRepository registrationRepository;

    public ReportService(VolunteerRepository volunteerRepository,
                         NssUnitRepository unitRepository,
                         EventRepository eventRepository,
                         ServiceHourEntryRepository serviceHourRepository,
                         UnitMembershipRepository membershipRepository,
                         EventRegistrationRepository registrationRepository) {
        this.volunteerRepository = volunteerRepository;
        this.unitRepository = unitRepository;
        this.eventRepository = eventRepository;
        this.serviceHourRepository = serviceHourRepository;
        this.membershipRepository = membershipRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional(readOnly = true)
    public InstitutionalMetricsResponse getInstitutionalMetrics() {
        long totalVolunteers = volunteerRepository.count();
        long activeVolunteers = volunteerRepository.countByStatus("ACTIVE");
        long totalUnits = unitRepository.count();
        long totalEvents = eventRepository.count();
        long completedEvents = eventRepository.countByStatus("COMPLETED");
        BigDecimal totalHours = serviceHourRepository.sumAllApprovedHours();

        List<NssUnit> units = unitRepository.findAll();
        List<UnitPerformanceMetric> unitMetrics = new ArrayList<>();

        for (NssUnit u : units) {
            long volCount = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(u.getUnitId()).size();
            long evCount = eventRepository.countByUnit_UnitId(u.getUnitId());
            BigDecimal unitHours = serviceHourRepository.sumApprovedHoursForUnit(u.getUnitId());

            unitMetrics.add(new UnitPerformanceMetric(
                u.getUnitId(),
                u.getUnitName(),
                u.getUnitNumber(),
                u.getOfficer() != null ? u.getOfficer().getName() : "Unassigned",
                volCount,
                evCount,
                unitHours
            ));
        }

        return new InstitutionalMetricsResponse(
            totalVolunteers,
            activeVolunteers,
            totalUnits,
            totalEvents,
            completedEvents,
            totalHours,
            unitMetrics
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportVolunteersCsv() {
        List<Volunteer> list = volunteerRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("Volunteer ID,Full Name,College ID,Department,Year of Study,Status,Enrollment Unit,Email\n");

        for (Volunteer v : list) {
            String unitName = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(v.getVolunteerId())
                .map(m -> m.getUnit().getUnitName()).orElse("Unassigned");

            sb.append(v.getVolunteerId()).append(",")
              .append(escapeCsv(v.getUser().getName())).append(",")
              .append(escapeCsv(v.getCollegeId())).append(",")
              .append(escapeCsv(v.getDepartment())).append(",")
              .append(v.getYearOfStudy()).append(",")
              .append(v.getStatus()).append(",")
              .append(escapeCsv(unitName)).append(",")
              .append(escapeCsv(v.getUser().getEmail())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportEventsCsv() {
        List<Event> list = eventRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("Event ID,Title,Event Type,NSS Unit,Start Time,End Time,Venue,Capacity,Status,Registered Count\n");

        for (Event e : list) {
            long registered = registrationRepository.countRegistered(e.getEventId());
            sb.append(e.getEventId()).append(",")
              .append(escapeCsv(e.getTitle())).append(",")
              .append(escapeCsv(e.getEventType())).append(",")
              .append(escapeCsv(e.getUnit().getUnitName())).append(",")
              .append(e.getStartAt()).append(",")
              .append(e.getEndAt()).append(",")
              .append(escapeCsv(e.getVenue())).append(",")
              .append(e.getCapacity()).append(",")
              .append(e.getStatus()).append(",")
              .append(registered).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportServiceHoursCsv() {
        List<ServiceHourEntry> list = serviceHourRepository.findByStatusOrderByCreatedAtDesc("APPROVED");
        StringBuilder sb = new StringBuilder();
        sb.append("Entry ID,Volunteer Name,College ID,Activity / Event,Hours,Status,Approved By,Created At\n");

        for (ServiceHourEntry s : list) {
            String activity = s.getEvent() != null ? s.getEvent().getTitle() : s.getDescription();
            String approvedBy = s.getApprovedBy() != null ? s.getApprovedBy().getName() : "System / QR Session";

            sb.append(s.getEntryId()).append(",")
              .append(escapeCsv(s.getVolunteer().getUser().getName())).append(",")
              .append(escapeCsv(s.getVolunteer().getCollegeId())).append(",")
              .append(escapeCsv(activity)).append(",")
              .append(s.getHours()).append(",")
              .append(s.getStatus()).append(",")
              .append(escapeCsv(approvedBy)).append(",")
              .append(s.getCreatedAt()).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
