package edu.college.nss.service;

import edu.college.nss.domain.Role;
import edu.college.nss.domain.UnitMembership;
import edu.college.nss.domain.User;
import edu.college.nss.domain.Volunteer;
import edu.college.nss.repository.RoleRepository;
import edu.college.nss.repository.UnitMembershipRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.repository.VolunteerRepository;
import edu.college.nss.web.dto.MembershipResponse;
import edu.college.nss.web.dto.VolunteerRequest;
import edu.college.nss.web.dto.VolunteerResponse;
import edu.college.nss.web.dto.VolunteerUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VolunteerService {

    private final VolunteerRepository volunteerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UnitMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public VolunteerService(
        VolunteerRepository volunteerRepository,
        UserRepository userRepository,
        RoleRepository roleRepository,
        UnitMembershipRepository membershipRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.volunteerRepository = volunteerRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
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

            Role volunteerRole = roleRepository.findByName("ROLE_VOLUNTEER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_VOLUNTEER", "Registered NSS Volunteer")));

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
    public VolunteerResponse getVolunteerById(Long volunteerId, UserDetails principal) {
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
        return volunteerRepository.searchVolunteers(search, status, department, pageable)
            .map(v -> {
                UnitMembership active = membershipRepository
                    .findByVolunteer_VolunteerIdAndIsActiveTrue(v.getVolunteerId())
                    .orElse(null);
                return VolunteerResponse.fromEntity(v, active);
            });
    }

    @Transactional
    public VolunteerResponse updateVolunteer(Long volunteerId, VolunteerUpdateRequest request, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        boolean isSelf = volunteer.getUser() != null &&
            volunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername());
        boolean isStaffOrAdmin = isStaffOrAdmin(principal);

        if (!isSelf && !isStaffOrAdmin) {
            throw new AccessDeniedException("You do not have permission to update this volunteer profile.");
        }

        if (request.phone() != null && volunteer.getUser() != null) {
            volunteer.getUser().setPhone(request.phone());
        }

        if (isStaffOrAdmin) {
            if (request.name() != null && !request.name().isBlank() && volunteer.getUser() != null) {
                volunteer.getUser().setName(request.name());
            }
            if (request.department() != null) {
                volunteer.setDepartment(request.department());
            }
            if (request.yearOfStudy() != null) {
                volunteer.setYearOfStudy(request.yearOfStudy());
            }
            if (request.status() != null) {
                volunteer.setStatus(request.status());
            }
        }

        volunteer = volunteerRepository.save(volunteer);

        UnitMembership active = membershipRepository
            .findByVolunteer_VolunteerIdAndIsActiveTrue(volunteerId)
            .orElse(null);

        return VolunteerResponse.fromEntity(volunteer, active);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> getVolunteerMemberships(Long volunteerId, UserDetails principal) {
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
            .orElseThrow(() -> new IllegalArgumentException("Volunteer not found with ID: " + volunteerId));

        validateViewAccess(volunteer, principal);

        return membershipRepository.findByVolunteer_VolunteerId(volunteerId)
            .stream()
            .map(MembershipResponse::fromEntity)
            .collect(Collectors.toList());
    }

    private void validateViewAccess(Volunteer volunteer, UserDetails principal) {
        if (isStaffOrAdmin(principal)) {
            return;
        }
        if (volunteer.getUser() != null &&
            volunteer.getUser().getEmail().equalsIgnoreCase(principal.getUsername())) {
            return;
        }
        throw new AccessDeniedException("Access is denied to this volunteer profile.");
    }

    private boolean isStaffOrAdmin(UserDetails principal) {
        return principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_ADMIN") ||
                           a.equals("ROLE_FACULTY_COORDINATOR") ||
                           a.equals("ROLE_PROGRAMME_OFFICER") ||
                           a.equals("VOLUNTEERS_MANAGE"));
    }
}
