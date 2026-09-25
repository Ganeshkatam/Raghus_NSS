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
import edu.college.nss.web.dto.UserDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
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
    private final AuditService auditService;

    public NssUnitService(
        NssUnitRepository unitRepository,
        UserRepository userRepository,
        VolunteerRepository volunteerRepository,
        UnitMembershipRepository membershipRepository,
        edu.college.nss.repository.UnitTransferHistoryRepository transferHistoryRepository,
        edu.college.nss.security.UnitSecurityService unitSecurity,
        edu.college.nss.repository.EventRepository eventRepository,
        edu.college.nss.repository.ServiceHourEntryRepository serviceHourRepository,
        NotificationService notificationService,
        AuditService auditService
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
        this.auditService = auditService;
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

        NssUnit unit = new NssUnit(request.unitName(), request.unitNumber(), officer, request.capacity());
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

    @Transactional(readOnly = true)
    public List<UserDto> getEligibleOfficers() {
        return userRepository.findAll().stream()
            .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
            .filter(u -> u.getRoles() != null && u.getRoles().stream()
                .anyMatch(r -> {
                    String name = r.getName().toUpperCase();
                    return name.contains("OFFICER") || name.contains("COORDINATOR") || name.contains("ADMIN");
                }))
            .map(UserDto::fromEntity)
            .sorted(Comparator.comparing(UserDto::name))
            .collect(Collectors.toList());
    }

    @Transactional
    public UnitResponse updateUnit(UUID unitId, UnitUpdateRequest request) {
        return updateUnit(unitId, request, null);
    }

    @Transactional
    public UnitResponse updateUnit(UUID unitId, UnitUpdateRequest request, org.springframework.security.core.userdetails.UserDetails principal) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        if (principal != null) {
            if (!unitSecurity.canManageUnit(principal, unitId)) {
                throw new org.springframework.security.access.AccessDeniedException("You are not authorized to update Unit " + unit.getUnitNumber());
            }
            if (!unitSecurity.isGlobalManager(principal) && (request.officerId() != null || Boolean.TRUE.equals(request.clearOfficer()))) {
                throw new org.springframework.security.access.AccessDeniedException("Programme Officers are not authorized to reassign unit officers.");
            }
        }

        if (request.unitName() != null && !request.unitName().isBlank()) {
            unit.setUnitName(request.unitName());
        }

        if (request.capacity() != null) {
            unit.setCapacity(request.capacity());
        }

        if (Boolean.TRUE.equals(request.clearOfficer())) {
            unit.setOfficer(null);
        } else if (request.officerId() != null) {
            User officer = userRepository.findById(request.officerId())
                .orElseThrow(() -> new IllegalArgumentException("Officer user not found with ID: " + request.officerId()));
            unit.setOfficer(officer);
        }

        unit = unitRepository.save(unit);
        long count = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId).size();
        if (principal != null) {
            auditService.logEvent(
                principal.getUsername(),
                "UNIT_UPDATE",
                "UNIT",
                unit.getUnitId().toString(),
                "Unit " + unit.getUnitNumber(),
                "CONFIGURED",
                "UPDATED",
                "Administrative unit settings update"
            );
        }
        return UnitResponse.fromEntity(unit, count);
    }

    @Transactional
    public MembershipResponse addMemberToUnit(UUID unitId, UUID volunteerId) {
        return addMemberToUnit(unitId, volunteerId, null);
    }

    @Transactional
    public MembershipResponse addMemberToUnit(UUID unitId, UUID volunteerId, org.springframework.security.core.userdetails.UserDetails principal) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        if (principal != null && !unitSecurity.canManageUnit(principal, unitId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to allot volunteers to Unit " + unit.getUnitNumber());
        }

        long currentCount = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId).size();
        if (unit.getCapacity() != null && currentCount >= unit.getCapacity()) {
            throw new IllegalStateException("Unit " + unit.getUnitName() + " has reached maximum capacity of " + unit.getCapacity() + " volunteers.");
        }

        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        // Enforce: One active volunteer = at most one active NSS Unit.
        // Allotment is ONLY for unallotted volunteers. Reject if already allotted to ANY unit.
        java.util.Optional<UnitMembership> existingActive = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId);
        if (existingActive.isPresent()) {
            UnitMembership active = existingActive.get();
            String currentUnitName = active.getUnit() != null ? active.getUnit().getUnitName() : "another unit";
            String currentUnitNumber = active.getUnit() != null ? active.getUnit().getUnitNumber() : "";
            String volName = volunteer.getUser() != null ? volunteer.getUser().getName() : "Volunteer";
            throw new IllegalStateException(volName + " is already actively allotted to " + currentUnitName +
                (currentUnitNumber.isBlank() ? "" : " (" + currentUnitNumber + ")") +
                ". Please use the explicit Transfer Volunteer action to move them between units.");
        }

        UnitMembership membership = new UnitMembership(volunteer, unit);
        membership = membershipRepository.save(membership);

        if (principal != null) {
            String volName = volunteer.getUser() != null ? volunteer.getUser().getName() : volunteer.getCollegeId();
            auditService.logEvent(
                principal.getUsername(),
                "UNIT_ALLOTMENT",
                "UNIT_MEMBERSHIP",
                membership.getMembershipId().toString(),
                volName + " -> Unit " + unit.getUnitNumber(),
                "UNALLOTTED",
                "ALLOTTED",
                "Volunteer allotted to Unit " + unit.getUnitNumber()
            );
        }

        if (volunteer.getUser() != null) {
            notificationService.sendNotification(
                volunteer.getUser(),
                "Unit Allotment Completed",
                "You have been allotted to NSS Unit: " + unit.getUnitName() + " (" + unit.getUnitNumber() + ").",
                "UNIT",
                "/units/" + unit.getUnitId()
            );
        }

        return MembershipResponse.fromEntity(membership);
    }

    @Transactional
    public MembershipResponse transferVolunteer(UUID unitIdInPath, edu.college.nss.web.dto.UnitTransferRequest request, org.springframework.security.core.userdetails.UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(request.volunteerId())
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found: " + request.volunteerId()));

        UnitMembership activeMembership = membershipRepository.findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId())
            .orElseThrow(() -> new IllegalStateException("Volunteer is not currently active in any unit. Use Allotment to assign them."));

        NssUnit sourceUnit = activeMembership.getUnit();
        UUID actualSourceUnitId = sourceUnit.getUnitId();

        UUID rawTargetUnitId = request.targetUnitId();
        if (rawTargetUnitId == null) {
            if (!unitIdInPath.equals(actualSourceUnitId)) {
                rawTargetUnitId = unitIdInPath;
            } else {
                throw new IllegalArgumentException("Target unit ID is required.");
            }
        }

        if (actualSourceUnitId.equals(rawTargetUnitId)) {
            throw new IllegalArgumentException("Target unit cannot be the same as the volunteer's current unit (" + sourceUnit.getUnitName() + ").");
        }

        final UUID targetUnitId = rawTargetUnitId;
        NssUnit targetUnit = unitRepository.findById(targetUnitId)
            .orElseThrow(() -> new IllegalArgumentException("Target NSS Unit not found: " + targetUnitId));

        // Authorization: Admin and Coordinator can transfer across any units.
        // Programme Officers can transfer if they manage the source unit OR the target unit.
        boolean canManageSource = unitSecurity.canManageUnit(principal, actualSourceUnitId);
        boolean canManageTarget = unitSecurity.canManageUnit(principal, targetUnitId);
        if (!canManageSource && !canManageTarget) {
            throw new org.springframework.security.access.AccessDeniedException(
                "You are not authorized to transfer volunteers between Unit " + sourceUnit.getUnitNumber() +
                " and Unit " + targetUnit.getUnitNumber()
            );
        }

        long targetActiveCount = membershipRepository.findByUnit_UnitIdAndIsActiveTrue(targetUnitId).size();
        if (targetUnit.getCapacity() != null && targetActiveCount >= targetUnit.getCapacity()) {
            throw new IllegalStateException("Target unit " + targetUnit.getUnitName() + " has reached maximum capacity.");
        }

        activeMembership.setIsActive(false);
        activeMembership.setLeftAt(Instant.now());
        membershipRepository.save(activeMembership);

        UnitMembership newMembership = new UnitMembership(volunteer, targetUnit);
        newMembership = membershipRepository.save(newMembership);

        User officer = principal != null ? userRepository.findByEmail(principal.getUsername()).orElse(null) : null;
        edu.college.nss.domain.UnitTransferHistory transferHistory = new edu.college.nss.domain.UnitTransferHistory(
            volunteer, sourceUnit, targetUnit, request.reason(), officer
        );
        transferHistoryRepository.save(transferHistory);

        if (principal != null) {
            String volName = volunteer.getUser() != null ? volunteer.getUser().getName() : volunteer.getCollegeId();
            auditService.logEvent(
                principal.getUsername(),
                "UNIT_TRANSFER",
                "UNIT_MEMBERSHIP",
                newMembership.getMembershipId().toString(),
                volName + " (from Unit " + sourceUnit.getUnitNumber() + " to Unit " + targetUnit.getUnitNumber() + ")",
                "Unit " + sourceUnit.getUnitNumber(),
                "Unit " + targetUnit.getUnitNumber(),
                request.reason() != null ? request.reason() : "Transfer authorized"
            );
        }

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
        return getVolunteerTransferHistory(volunteerId, null);
    }

    @Transactional(readOnly = true)
    public List<edu.college.nss.web.dto.UnitTransferHistoryResponse> getVolunteerTransferHistory(UUID volunteerId, org.springframework.security.core.userdetails.UserDetails principal) {
        if (principal != null && !unitSecurity.canViewVolunteerTransferHistory(principal, volunteerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view this volunteer's transfer history.");
        }

        return transferHistoryRepository.findByVolunteer_VolunteerIdOrderByTransferredAtDesc(volunteerId)
            .stream()
            .map(edu.college.nss.web.dto.UnitTransferHistoryResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getUnitMembers(UUID unitId) {
        return getUnitMembers(unitId, null);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getUnitMembers(UUID unitId, org.springframework.security.core.userdetails.UserDetails principal) {
        unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        if (principal != null && !unitSecurity.canViewUnitMembers(principal, unitId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view the member roster for this unit.");
        }

        return membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId)
            .stream()
            .map(MembershipResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public MembershipResponse deactivateMembership(UUID unitId, UUID membershipId) {
        return deactivateMembership(unitId, membershipId, null);
    }

    @Transactional
    public MembershipResponse deactivateMembership(UUID unitId, UUID membershipId, org.springframework.security.core.userdetails.UserDetails principal) {
        if (principal != null && !unitSecurity.canManageUnit(principal, unitId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to remove volunteers from this unit.");
        }

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
