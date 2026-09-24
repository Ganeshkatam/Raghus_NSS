package edu.college.nss.service;

import edu.college.nss.domain.NssUnit;
import edu.college.nss.domain.UnitMembership;
import edu.college.nss.domain.User;
import edu.college.nss.domain.Volunteer;
import edu.college.nss.repository.NssUnitRepository;
import edu.college.nss.repository.UnitMembershipRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.repository.VolunteerRepository;
import edu.college.nss.web.dto.MembershipResponse;
import edu.college.nss.web.dto.UnitRequest;
import edu.college.nss.web.dto.UnitResponse;
import edu.college.nss.web.dto.UnitUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NssUnitService {

    private final NssUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final VolunteerRepository volunteerRepository;
    private final UnitMembershipRepository membershipRepository;
    private final edu.college.nss.repository.UnitTransferHistoryRepository transferHistoryRepository;
    private final edu.college.nss.security.UnitSecurityService unitSecurity;
    private final edu.college.nss.repository.EventRepository eventRepository;
    private final edu.college.nss.repository.ServiceHourEntryRepository serviceHourRepository;
    private final NotificationService notificationService;

    public NssUnitService(
        NssUnitRepository unitRepository,
        UserRepository userRepository,
        VolunteerRepository volunteerRepository,
        UnitMembershipRepository membershipRepository,
        edu.college.nss.repository.UnitTransferHistoryRepository transferHistoryRepository,
        edu.college.nss.security.UnitSecurityService unitSecurity,
        edu.college.nss.repository.EventRepository eventRepository,
        edu.college.nss.repository.ServiceHourEntryRepository serviceHourRepository,
        NotificationService notificationService
    ) {
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.volunteerRepository = volunteerRepository;
        this.membershipRepository = membershipRepository;
        this.transferHistoryRepository = transferHistoryRepository;
        this.unitSecurity = unitSecurity;
        this.eventRepository = eventRepository;
        this.serviceHourRepository = serviceHourRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public UnitResponse createUnit(UnitRequest request) {
        if (unitRepository.existsByUnitNumber(request.unitNumber())) {
            throw new IllegalArgumentException("Unit number already exists: " + request.unitNumber());
        }

        User officer = null;
        if (request.officerId() != null) {
            officer = userRepository.findById(request.officerId())
                .orElseThrow(() -> new IllegalArgumentException("Officer user not found with ID: " + request.officerId()));
        }

        NssUnit unit = new NssUnit(request.unitName(), request.unitNumber(), officer);
        unit = unitRepository.save(unit);

        return UnitResponse.fromEntity(unit, 0);
    }

    @Transactional(readOnly = true)
    public UnitResponse getUnitById(UUID unitId) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        long count = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId).size();
        return UnitResponse.fromEntity(unit, count);
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> getAllUnits() {
        return unitRepository.findAll().stream()
            .map(u -> {
                long count = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(u.getUnitId()).size();
                return UnitResponse.fromEntity(u, count);
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public UnitResponse updateUnit(UUID unitId, UnitUpdateRequest request) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        if (request.unitName() != null && !request.unitName().isBlank()) {
            unit.setUnitName(request.unitName());
        }

        if (request.officerId() != null) {
            User officer = userRepository.findById(request.officerId())
                .orElseThrow(() -> new IllegalArgumentException("Officer user not found with ID: " + request.officerId()));
            unit.setOfficer(officer);
        }

        unit = unitRepository.save(unit);
        long count = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId).size();
        return UnitResponse.fromEntity(unit, count);
    }

    @Transactional
    public MembershipResponse addMemberToUnit(UUID unitId, UUID volunteerId) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        long currentCount = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId).size();
        if (currentCount >= unit.getCapacity()) {
            throw new IllegalStateException("Unit " + unit.getUnitName() + " has reached maximum capacity of " + unit.getCapacity() + " volunteers.");
        }

        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        // If volunteer currently has an active membership, deactivate it to preserve history
        membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId)
            .ifPresent(active -> {
                active.setIsActive(false);
                active.setLeftAt(Instant.now());
                membershipRepository.save(active);
            });

        UnitMembership membership = new UnitMembership(volunteer, unit);
        membership = membershipRepository.save(membership);

        return MembershipResponse.fromEntity(membership);
    }

    @Transactional
    public MembershipResponse transferVolunteer(UUID sourceUnitId, edu.college.nss.web.dto.UnitTransferRequest request, org.springframework.security.core.userdetails.UserDetails principal) {
        NssUnit sourceUnit = unitRepository.findById(sourceUnitId)
            .orElseThrow(() -> new IllegalArgumentException("Source NSS Unit not found: " + sourceUnitId));
        NssUnit targetUnit = unitRepository.findById(request.targetUnitId())
            .orElseThrow(() -> new IllegalArgumentException("Target NSS Unit not found: " + request.targetUnitId()));

        if (!unitSecurity.canManageUnit(principal, sourceUnitId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to transfer volunteers out of Unit " + sourceUnit.getUnitNumber());
        }

        long targetActiveCount = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(request.targetUnitId()).size();
        if (targetActiveCount >= targetUnit.getCapacity()) {
            throw new IllegalStateException("Target unit " + targetUnit.getUnitName() + " has reached capacity.");
        }

        Volunteer volunteer = volunteerRepository.findById(request.volunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found: " + request.volunteerId()));

        membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId())
            .ifPresent(active -> {
                active.setIsActive(false);
                active.setLeftAt(Instant.now());
                membershipRepository.save(active);
            });

        UnitMembership newMembership = new UnitMembership(volunteer, targetUnit);
        newMembership = membershipRepository.save(newMembership);

        User officer = userRepository.findByEmail(principal.getUsername()).orElse(null);
        edu.college.nss.domain.UnitTransferHistory transferHistory = new edu.college.nss.domain.UnitTransferHistory(
            volunteer, sourceUnit, targetUnit, request.reason(), officer
        );
        transferHistoryRepository.save(transferHistory);

        if (volunteer.getUser() != null) {
            notificationService.sendNotification(
                volunteer.getUser(),
                "Unit Transfer Completed",
                "You have been transferred from " + sourceUnit.getUnitName() + " to " + targetUnit.getUnitName() + (request.reason() != null ? ": " + request.reason() : ""),
                "UNIT",
                "/units/" + targetUnit.getUnitId()
            );
        }

        return MembershipResponse.fromEntity(newMembership);
    }

    @Transactional(readOnly = true)
    public edu.college.nss.web.dto.UnitStatsResponse getUnitStats(UUID unitId) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitId));

        long activeVolunteers = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId).size();
        long totalEvents = eventRepository.countByUnit_UnitId(unitId);
        java.math.BigDecimal hoursSum = serviceHourRepository.sumApprovedHoursForUnit(unitId);
        double totalHours = hoursSum != null ? hoursSum.doubleValue() : 0.0;

        return new edu.college.nss.web.dto.UnitStatsResponse(
            unit.getUnitId(),
            unit.getUnitName(),
            unit.getUnitNumber(),
            unit.getCapacity(),
            activeVolunteers,
            totalEvents,
            totalHours
        );
    }

    @Transactional(readOnly = true)
    public List<edu.college.nss.web.dto.UnitTransferHistoryResponse> getVolunteerTransferHistory(UUID volunteerId) {
        return transferHistoryRepository.findByVolunteer_VolunteerIdOrderByTransferredAtDesc(volunteerId)
            .stream()
            .map(edu.college.nss.web.dto.UnitTransferHistoryResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getUnitMembers(UUID unitId) {
        unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        return membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId)
            .stream()
            .map(MembershipResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public MembershipResponse deactivateMembership(UUID unitId, UUID membershipId) {
        UnitMembership membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new IllegalArgumentException("Membership not found with ID: " + membershipId));

        if (!membership.getUnit().getUnitId().equals(unitId)) {
            throw new IllegalArgumentException("Membership does not belong to unit ID: " + unitId);
        }

        membership.setIsActive(false);
        membership.setLeftAt(Instant.now());
        membership = membershipRepository.save(membership);

        return MembershipResponse.fromEntity(membership);
    }
}
