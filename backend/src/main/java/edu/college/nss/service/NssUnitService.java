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
import java.util.stream.Collectors;

@Service
public class NssUnitService {

    private final NssUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final VolunteerRepository volunteerRepository;
    private final UnitMembershipRepository membershipRepository;

    public NssUnitService(
        NssUnitRepository unitRepository,
        UserRepository userRepository,
        VolunteerRepository volunteerRepository,
        UnitMembershipRepository membershipRepository
    ) {
        this.unitRepository = unitRepository;
        this.userRepository = userRepository;
        this.volunteerRepository = volunteerRepository;
        this.membershipRepository = membershipRepository;
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
    public UnitResponse getUnitById(Long unitId) {
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
    public UnitResponse updateUnit(Long unitId, UnitUpdateRequest request) {
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
    public MembershipResponse addMemberToUnit(Long unitId, Long volunteerId) {
        NssUnit unit = unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

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

    @Transactional(readOnly = true)
    public List<MembershipResponse> getUnitMembers(Long unitId) {
        unitRepository.findById(unitId)
            .orElseThrow(() -> new IllegalArgumentException("NSS Unit not found with ID: " + unitId));

        return membershipRepository.findByUnit_UnitIdAndIsActiveTrue(unitId)
            .stream()
            .map(MembershipResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional
    public MembershipResponse deactivateMembership(Long unitId, Long membershipId) {
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
