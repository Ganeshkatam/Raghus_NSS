package edu.college.nss.service;

import edu.college.nss.domain.Role;
import edu.college.nss.domain.UnitMembership;
import edu.college.nss.domain.User;
import edu.college.nss.domain.Volunteer;
import edu.college.nss.domain.VolunteerStatusHistory;
import edu.college.nss.repository.RoleRepository;
import edu.college.nss.repository.UnitMembershipRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.repository.VolunteerRepository;
import edu.college.nss.repository.VolunteerStatusHistoryRepository;
import edu.college.nss.security.UnitSecurityService;
import edu.college.nss.web.dto.MembershipResponse;
import edu.college.nss.web.dto.VolunteerRequest;
import edu.college.nss.web.dto.VolunteerResponse;
import edu.college.nss.web.dto.VolunteerStatusHistoryResponse;
import edu.college.nss.web.dto.VolunteerStatusUpdateRequest;
import edu.college.nss.web.dto.VolunteerUpdateRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VolunteerService {

    private final VolunteerRepository volunteerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UnitMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final VolunteerStatusHistoryRepository statusHistoryRepository;
    private final UnitSecurityService unitSecurity;
    private final NotificationService notificationService;

    public VolunteerService(
        VolunteerRepository volunteerRepository,
        UserRepository userRepository,
        RoleRepository roleRepository,
        UnitMembershipRepository membershipRepository,
        PasswordEncoder passwordEncoder,
        VolunteerStatusHistoryRepository statusHistoryRepository,
        UnitSecurityService unitSecurity,
        NotificationService notificationService
    ) {
        this.volunteerRepository = volunteerRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.statusHistoryRepository = statusHistoryRepository;
        this.unitSecurity = unitSecurity;
        this.notificationService = notificationService;
    }

    @Transactional
    public VolunteerResponse createVolunteer(VolunteerRequest request) {
        if (volunteerRepository.existsByCollegeId(request.collegeId())) {
            throw new IllegalArgumentException("Volunteer already exists with college ID: " + request.collegeId());
        }

        String email = request.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password() : "Volunteer@123";

            Role volunteerRole = roleRepository.findByName("VOLUNTEER")
                .orElseGet(() -> roleRepository.save(new Role("VOLUNTEER", "Registered NSS Volunteer")));

            user = new User(request.name(), email, passwordEncoder.encode(rawPassword), request.phone());
            user.setRoles(Set.of(volunteerRole));
            user = userRepository.save(user);
        } else {
            if (volunteerRepository.existsByUser_UserId(user.getUserId())) {
                throw new IllegalArgumentException("User " + email + " is already linked to a volunteer profile.");
            }
        }

        Volunteer volunteer = new Volunteer(user, request.collegeId(), request.department(), request.yearOfStudy());
        volunteer = volunteerRepository.save(volunteer);

        return VolunteerResponse.fromEntity(volunteer, null);
    }

    @Transactional(readOnly = true)
    public VolunteerResponse getVolunteerById(UUID volunteerId, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        validateViewAccess(volunteer, principal);

        UnitMembership activeMembership = membershipRepository
            .findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId)
            .orElse(null);

        return VolunteerResponse.fromEntity(volunteer, activeMembership);
    }

    @Transactional(readOnly = true)
    public Page<VolunteerResponse> searchVolunteers(
        String search, String status, String department, Pageable pageable
    ) {
        return searchVolunteers(search, status, department, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<VolunteerResponse> searchVolunteers(
        String search, String status, String department, Pageable pageable, UserDetails principal
    ) {
        if (principal != null && !unitSecurity.isGlobalManager(principal)) {
            List<UUID> managedUnitIds = unitSecurity.getManagedUnitIds(principal);
            if (managedUnitIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        Specification<Volunteer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Join<Volunteer, User> userJoin = root.join("user", JoinType.LEFT);
                Predicate nameMatch = cb.like(cb.lower(userJoin.get("name")), pattern);
                Predicate collegeIdMatch = cb.like(cb.lower(root.get("collegeId")), pattern);
                predicates.add(cb.or(nameMatch, collegeIdMatch));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (department != null && !department.isBlank()) {
                predicates.add(cb.equal(root.get("department"), department.trim()));
            }

            // Programme Officers can only search volunteers within their assigned NSS unit(s)
            if (principal != null && !unitSecurity.isGlobalManager(principal)) {
                List<UUID> managedUnitIds = unitSecurity.getManagedUnitIds(principal);
                Subquery<UUID> subquery = query.subquery(UUID.class);
                Root<UnitMembership> memRoot = subquery.from(UnitMembership.class);
                subquery.select(memRoot.get("volunteer").get("volunteerId"))
                    .where(
                        cb.isTrue(memRoot.get("isActive")),
                        memRoot.get("unit").get("unitId").in(managedUnitIds)
                    );
                predicates.add(root.get("volunteerId").in(subquery));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return volunteerRepository.findAll(spec, pageable)
            .map(v -> {
                UnitMembership active = membershipRepository
                    .findByVolunteer_VolunteerIdAndIsActiveTrue(v.getVolunteerId())
                    .orElse(null);
                return VolunteerResponse.fromEntity(v, active);
            });
    }

    @Transactional
    public VolunteerResponse updateVolunteer(UUID volunteerId, VolunteerUpdateRequest request, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        boolean isSelf = volunteer.getUser() != null &&
            volunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername());
        boolean canManage = unitSecurity.isGlobalManager(principal) || unitSecurity.canManageVolunteer(principal, volunteerId);

        if (!isSelf && !canManage) {
            throw new AccessDeniedException("You do not have permission to update this volunteer profile.");
        }

        if (request.phone() != null && volunteer.getUser() != null) {
            volunteer.getUser().setPhone(request.phone());
        }

        if (canManage) {
            if (request.name() != null && !request.name().isBlank() && volunteer.getUser() != null) {
                volunteer.getUser().setName(request.name());
            }
            if (request.department() != null) {
                volunteer.setDepartment(request.department());
            }
            if (request.yearOfStudy() != null) {
                volunteer.setYearOfStudy(request.yearOfStudy());
            }
            if (request.status() != null && !request.status().equalsIgnoreCase(volunteer.getStatus())) {
                updateVolunteerStatus(volunteerId, new VolunteerStatusUpdateRequest(request.status(), "Updated by leadership"), principal);
            }
        }

        volunteer = volunteerRepository.save(volunteer);

        UnitMembership active = membershipRepository
            .findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId)
            .orElse(null);

        return VolunteerResponse.fromEntity(volunteer, active);
    }

    @Transactional
    public VolunteerResponse updateVolunteerStatus(UUID volunteerId, VolunteerStatusUpdateRequest request, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        boolean canManage = unitSecurity.isGlobalManager(principal) || unitSecurity.canManageVolunteer(principal, volunteerId);
        if (!canManage) {
            throw new AccessDeniedException("You do not have permission to change status for this volunteer.");
        }

        String targetStatus = request.status().toUpperCase().trim();
        List<String> validStatuses = List.of("PENDING_APPROVAL", "ACTIVE", "INACTIVE", "SUSPENDED", "ALUMNI");
        if (!validStatuses.contains(targetStatus)) {
            throw new IllegalArgumentException("Invalid volunteer status: " + request.status());
        }

        String previousStatus = volunteer.getStatus();
        volunteer.setStatus(targetStatus);
        volunteer = volunteerRepository.save(volunteer);

        User officer = userRepository.findByEmail(principal.getUsername()).orElse(null);
        VolunteerStatusHistory history = new VolunteerStatusHistory(
            volunteer,
            previousStatus,
            targetStatus,
            request.reason() != null ? request.reason() : "Status updated to " + targetStatus,
            officer
        );
        statusHistoryRepository.save(history);

        if (volunteer.getUser() != null) {
            notificationService.sendNotification(
                volunteer.getUser(),
                "Volunteer Status Update",
                "Your NSS volunteer membership status is now: " + targetStatus + (request.reason() != null ? " (" + request.reason() + ")" : ""),
                "VOLUNTEER",
                "/volunteers/" + volunteer.getVolunteerId()
            );
        }

        UnitMembership active = membershipRepository
            .findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId)
            .orElse(null);

        return VolunteerResponse.fromEntity(volunteer, active);
    }

    @Transactional(readOnly = true)
    public List<VolunteerStatusHistoryResponse> getVolunteerStatusHistory(UUID volunteerId, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        validateViewAccess(volunteer, principal);

        return statusHistoryRepository.findByVolunteer_VolunteerIdOrderByCreatedAtDesc(volunteerId)
            .stream()
            .map(VolunteerStatusHistoryResponse::fromEntity)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VolunteerResponse getCurrentVolunteer(UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findByUser_Email(principal.getUsername()).orElse(null);
        if (volunteer == null) {
            return null;
        }
        UnitMembership active = membershipRepository
            .findByVolunteer_VolunteerIdAndIsActiveTrue(volunteer.getVolunteerId())
            .orElse(null);
        return VolunteerResponse.fromEntity(volunteer, active);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getVolunteerMemberships(UUID volunteerId, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        validateViewAccess(volunteer, principal);

        return membershipRepository.findByVolunteer_VolunteerId(volunteerId)
            .stream()
            .map(MembershipResponse::fromEntity)
            .collect(Collectors.toList());
    }

    private void validateViewAccess(Volunteer volunteer, UserDetails principal) {
        if (unitSecurity.isGlobalManager(principal)) {
            return;
        }
        if (volunteer.getUser() != null &&
            volunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername())) {
            return;
        }
        if (unitSecurity.canManageVolunteer(principal, volunteer.getVolunteerId())) {
            return;
        }
        throw new AccessDeniedException("Access is denied to this volunteer profile.");
    }
}
