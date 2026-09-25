package edu.college.nss.service;

import edu.college.nss.domain.*;
import edu.college.nss.repository.*;
import edu.college.nss.web.dto.DashboardSummaryResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final VolunteerRepository volunteerRepository;
    private final NssUnitRepository unitRepository;
    private final UnitMembershipRepository membershipRepository;
    private final EventRepository eventRepository;
    private final ServiceHourEntryRepository serviceHourRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final NotificationRepository notificationRepository;

    public DashboardService(
        UserRepository userRepository,
        VolunteerRepository volunteerRepository,
        NssUnitRepository unitRepository,
        UnitMembershipRepository membershipRepository,
        EventRepository eventRepository,
        ServiceHourEntryRepository serviceHourRepository,
        AttendanceCorrectionRepository correctionRepository,
        NotificationRepository notificationRepository
    ) {
        this.userRepository = userRepository;
        this.volunteerRepository = volunteerRepository;
        this.unitRepository = unitRepository;
        this.membershipRepository = membershipRepository;
        this.eventRepository = eventRepository;
        this.serviceHourRepository = serviceHourRepository;
        this.correctionRepository = correctionRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getUsername()));

        boolean isAdmin = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_ADMIN"));
        boolean isCoordinator = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_FACULTY_COORDINATOR"));
        boolean isOfficer = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_PROGRAMME_OFFICER"));

        String primaryRole = isAdmin ? "ADMIN"
            : isCoordinator ? "FACULTY_COORDINATOR"
            : isOfficer ? "PROGRAMME_OFFICER"
            : "VOLUNTEER";

        Map<String, Object> volunteerMap = null;
        Map<String, Object> officerMap = null;
        Map<String, Object> institutionalMap = null;

        if ("VOLUNTEER".equals(primaryRole)) {
            volunteerMap = new HashMap<>();
            Optional<Volunteer> volOpt = volunteerRepository.findByUser_UserId(user.getUserId());
            if (volOpt.isPresent()) {
                Volunteer vol = volOpt.get();
                volunteerMap.put("volunteerId", vol.getVolunteerId());
                volunteerMap.put("collegeId", vol.getCollegeId());
                volunteerMap.put("department", vol.getDepartment());
                volunteerMap.put("yearOfStudy", vol.getYearOfStudy());
                volunteerMap.put("status", vol.getStatus());

                Optional<UnitMembership> activeMem = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(vol.getVolunteerId());
                if (activeMem.isPresent()) {
                    NssUnit unit = activeMem.get().getUnit();
                    volunteerMap.put("activeUnitId", unit.getUnitId());
                    volunteerMap.put("activeUnitNumber", unit.getUnitNumber());
                    volunteerMap.put("activeUnitName", unit.getUnitName());
                }

                BigDecimal totalApprovedHours = serviceHourRepository.sumApprovedHoursForVolunteer(vol.getVolunteerId());
                List<ServiceHourEntry> volunteerEntries = serviceHourRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(vol.getVolunteerId());
                long pendingClaims = volunteerEntries.stream().filter(e -> "PENDING".equalsIgnoreCase(e.getStatus())).count();

                volunteerMap.put("totalApprovedHours", totalApprovedHours != null ? totalApprovedHours.doubleValue() : 0.0);
                volunteerMap.put("pendingClaims", pendingClaims);
            }
            long unreadNotifications = notificationRepository.countByUser_UserIdAndIsReadFalse(user.getUserId());
            volunteerMap.put("unreadNotifications", unreadNotifications);
        } else if ("PROGRAMME_OFFICER".equals(primaryRole)) {
            officerMap = new HashMap<>();
            List<NssUnit> officerUnits = unitRepository.findByOfficer_UserId(user.getUserId());
            NssUnit myUnit = officerUnits.isEmpty() ? null : officerUnits.get(0);
            if (myUnit != null) {
                officerMap.put("unitId", myUnit.getUnitId());
                officerMap.put("unitNumber", myUnit.getUnitNumber());
                officerMap.put("unitName", myUnit.getUnitName());
                officerMap.put("capacity", myUnit.getCapacity());
                int activeMembers = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(myUnit.getUnitId()).size();
                officerMap.put("activeMemberCount", activeMembers);
            }
            long pendingApprovals = volunteerRepository.countByStatus("PENDING_APPROVAL");
            long pendingClaims = serviceHourRepository.findByStatusOrderByCreatedAtDesc("PENDING").size();
            long pendingCorrections = correctionRepository.findByStatusOrderByCorrectedAtDesc("PENDING").size();

            officerMap.put("pendingApprovals", pendingApprovals);
            officerMap.put("pendingClaims", pendingClaims);
            officerMap.put("pendingCorrections", pendingCorrections);
        } else {
            institutionalMap = new HashMap<>();
            long totalVols = volunteerRepository.count();
            long activeVols = volunteerRepository.countByStatus("ACTIVE");
            long totalUnits = unitRepository.count();
            long totalEvents = eventRepository.count();
            long completedEvents = eventRepository.countByStatus("COMPLETED");
            BigDecimal totalHours = serviceHourRepository.sumAllApprovedHours();
            long pendingApprovals = volunteerRepository.countByStatus("PENDING_APPROVAL");
            long pendingClaims = serviceHourRepository.findByStatusOrderByCreatedAtDesc("PENDING").size();

            institutionalMap.put("totalVolunteers", totalVols);
            institutionalMap.put("activeVolunteers", activeVols);
            institutionalMap.put("totalUnits", totalUnits);
            institutionalMap.put("totalEvents", totalEvents);
            institutionalMap.put("completedEvents", completedEvents);
            institutionalMap.put("totalServiceHours", totalHours != null ? totalHours.doubleValue() : 0.0);
            institutionalMap.put("pendingApprovals", pendingApprovals);
            institutionalMap.put("pendingClaims", pendingClaims);
        }

        return new DashboardSummaryResponse(
            primaryRole,
            user.getName(),
            user.getEmail(),
            volunteerMap,
            officerMap,
            institutionalMap
        );
    }
}
