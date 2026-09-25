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
import java.util.UUID;

@Service
public class ReportService {

    private final VolunteerRepository volunteerRepository;
    private final NssUnitRepository unitRepository;
    private final EventRepository eventRepository;
    private final ServiceHourEntryRepository serviceHourRepository;
    private final UnitMembershipRepository membershipRepository;
    private final EventRegistrationRepository registrationRepository;
    private final edu.college.nss.security.UnitSecurityService unitSecurity;

    public ReportService(VolunteerRepository volunteerRepository,
                         NssUnitRepository unitRepository,
                         EventRepository eventRepository,
                         ServiceHourEntryRepository serviceHourRepository,
                         UnitMembershipRepository membershipRepository,
                         EventRegistrationRepository registrationRepository,
                         edu.college.nss.security.UnitSecurityService unitSecurity) {
        this.volunteerRepository = volunteerRepository;
        this.unitRepository = unitRepository;
        this.eventRepository = eventRepository;
        this.serviceHourRepository = serviceHourRepository;
        this.membershipRepository = membershipRepository;
        this.registrationRepository = registrationRepository;
        this.unitSecurity = unitSecurity;
    }

    @Transactional(readOnly = true)
    public InstitutionalMetricsResponse getInstitutionalMetrics() {
        return getInstitutionalMetrics(null);
    }

    @Transactional(readOnly = true)
    public InstitutionalMetricsResponse getInstitutionalMetrics(org.springframework.security.core.userdetails.UserDetails principal) {
        List<NssUnit> units = unitRepository.findAll();
        if (principal != null && !unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedIds = unitSecurity.getManagedUnitIds(principal);
            units = units.stream().filter(u -> managedIds.contains(u.getUnitId())).toList();
        }

        List<UnitPerformanceMetric> unitMetrics = new ArrayList<>();
        long totalVolunteersCount = 0;
        long totalEventsCount = 0;
        long completedEventsCount = 0;
        BigDecimal totalHoursSum = BigDecimal.ZERO;

        for (NssUnit u : units) {
            long volCount = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(u.getUnitId()).size();
            long evCount = eventRepository.countByUnit_UnitId(u.getUnitId());
            BigDecimal unitHours = serviceHourRepository.sumApprovedHoursForUnit(u.getUnitId());
            if (unitHours != null) {
                totalHoursSum = totalHoursSum.add(unitHours);
            }
            totalVolunteersCount += volCount;
            totalEventsCount += evCount;
            completedEventsCount += eventRepository.findAll().stream()
                .filter(e -> u.getUnitId().equals(e.getUnit().getUnitId()) && "COMPLETED".equalsIgnoreCase(e.getStatus()))
                .count();

            unitMetrics.add(new UnitPerformanceMetric(
                u.getUnitId(),
                u.getUnitName(),
                u.getUnitNumber(),
                u.getOfficer() != null ? u.getOfficer().getName() : "Unassigned",
                volCount,
                evCount,
                unitHours != null ? unitHours : BigDecimal.ZERO
            ));
        }

        long totalUnits = units.size();
        long activeVolunteers = totalVolunteersCount;
        if (principal == null || unitSecurity.isGlobalManager(principal)) {
            totalVolunteersCount = volunteerRepository.count();
            activeVolunteers = volunteerRepository.countByStatus("ACTIVE");
            totalEventsCount = eventRepository.count();
            completedEventsCount = eventRepository.countByStatus("COMPLETED");
            BigDecimal allApproved = serviceHourRepository.sumAllApprovedHours();
            totalHoursSum = allApproved != null ? allApproved : BigDecimal.ZERO;
        }

        return new InstitutionalMetricsResponse(
            totalVolunteersCount,
            activeVolunteers,
            totalUnits,
            totalEventsCount,
            completedEventsCount,
            totalHoursSum,
            unitMetrics
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportVolunteersCsv() {
        return exportVolunteersCsv(null, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportVolunteersCsv(UUID unitId, String status) {
        return exportVolunteersCsv(unitId, status, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportVolunteersCsv(UUID unitId, String status, org.springframework.security.core.userdetails.UserDetails principal) {
        List<Volunteer> list = volunteerRepository.findAll();

        if (principal != null && !unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedIds = unitSecurity.getManagedUnitIds(principal);
            if (unitId != null) {
                if (!managedIds.contains(unitId)) {
                    throw new org.springframework.security.access.AccessDeniedException("You are not authorized to export roster for this unit.");
                }
            } else {
                if (managedIds.isEmpty()) return new byte[0];
                list = list.stream().filter(v -> {
                    UnitMembership m = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(v.getVolunteerId()).orElse(null);
                    return m != null && m.getUnit() != null && managedIds.contains(m.getUnit().getUnitId());
                }).toList();
            }
        }

        if (status != null && !status.isBlank()) {
            list = list.stream().filter(v -> status.equalsIgnoreCase(v.getStatus())).toList();
        }
        if (unitId != null) {
            list = list.stream().filter(v -> {
                UnitMembership m = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(v.getVolunteerId()).orElse(null);
                return m != null && m.getUnit() != null && unitId.equals(m.getUnit().getUnitId());
            }).toList();
        }

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
        return exportEventsCsv(null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportEventsCsv(UUID unitId, String status, String startDate, String endDate) {
        return exportEventsCsv(unitId, status, startDate, endDate, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportEventsCsv(UUID unitId, String status, String startDate, String endDate, org.springframework.security.core.userdetails.UserDetails principal) {
        List<Event> list = eventRepository.findAll();

        if (principal != null && !unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedIds = unitSecurity.getManagedUnitIds(principal);
            if (unitId != null) {
                if (!managedIds.contains(unitId)) {
                    throw new org.springframework.security.access.AccessDeniedException("You are not authorized to export events for this unit.");
                }
            } else {
                if (managedIds.isEmpty()) return new byte[0];
                list = list.stream().filter(e -> managedIds.contains(e.getUnit().getUnitId())).toList();
            }
        }

        if (unitId != null) {
            list = list.stream().filter(e -> unitId.equals(e.getUnit().getUnitId())).toList();
        }
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(e -> status.equalsIgnoreCase(e.getStatus())).toList();
        }
        if (startDate != null && !startDate.isBlank()) {
            try {
                java.time.Instant start = java.time.Instant.parse(startDate);
                list = list.stream().filter(e -> !e.getStartAt().isBefore(start)).toList();
            } catch (Exception ignored) {}
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                java.time.Instant end = java.time.Instant.parse(endDate);
                list = list.stream().filter(e -> !e.getEndAt().isAfter(end)).toList();
            } catch (Exception ignored) {}
        }

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
        return exportServiceHoursCsv(null, null, null, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportServiceHoursCsv(UUID unitId, String startDate, String endDate) {
        return exportServiceHoursCsv(unitId, startDate, endDate, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportServiceHoursCsv(UUID unitId, String startDate, String endDate, org.springframework.security.core.userdetails.UserDetails principal) {
        List<ServiceHourEntry> list = serviceHourRepository.findByStatusOrderByCreatedAtDesc("APPROVED");

        if (principal != null && !unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedIds = unitSecurity.getManagedUnitIds(principal);
            if (unitId != null) {
                if (!managedIds.contains(unitId)) {
                    throw new org.springframework.security.access.AccessDeniedException("You are not authorized to export service hours for this unit.");
                }
            } else {
                if (managedIds.isEmpty()) return new byte[0];
                list = list.stream().filter(s -> {
                    UnitMembership m = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(s.getVolunteer().getVolunteerId()).orElse(null);
                    return m != null && m.getUnit() != null && managedIds.contains(m.getUnit().getUnitId());
                }).toList();
            }
        }

        if (unitId != null) {
            list = list.stream().filter(s -> {
                UnitMembership m = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(s.getVolunteer().getVolunteerId()).orElse(null);
                return m != null && m.getUnit() != null && unitId.equals(m.getUnit().getUnitId());
            }).toList();
        }
        if (startDate != null && !startDate.isBlank()) {
            try {
                java.time.Instant start = java.time.Instant.parse(startDate);
                list = list.stream().filter(s -> !s.getCreatedAt().isBefore(start)).toList();
            } catch (Exception ignored) {}
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                java.time.Instant end = java.time.Instant.parse(endDate);
                list = list.stream().filter(s -> !s.getCreatedAt().isAfter(end)).toList();
            } catch (Exception ignored) {}
        }

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
