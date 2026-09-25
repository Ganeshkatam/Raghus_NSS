package edu.college.nss.web;

import edu.college.nss.domain.Role;
import edu.college.nss.domain.User;
import edu.college.nss.repository.RoleRepository;
import edu.college.nss.repository.UnitTransferHistoryRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.repository.VolunteerStatusHistoryRepository;
import edu.college.nss.service.AuditService;
import edu.college.nss.web.dto.AuditLogItem;
import edu.college.nss.web.dto.CreateUserRequest;
import edu.college.nss.web.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VolunteerStatusHistoryRepository statusHistoryRepository;
    private final UnitTransferHistoryRepository transferHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          VolunteerStatusHistoryRepository statusHistoryRepository,
                          UnitTransferHistoryRepository transferHistoryRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.transferHistoryRepository = transferHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UserDto> createUser(
        @Valid @RequestBody CreateUserRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("User with email " + email + " already exists.");
        }

        String rawPassword = (request.password() != null && !request.password().isBlank())
            ? request.password()
            : generateSecurePassword();

        String roleName = request.role().toUpperCase().trim();
        String fullRoleName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        Role role = roleRepository.findByName(fullRoleName)
            .orElseGet(() -> roleRepository.save(new Role(fullRoleName, roleName + " System Role")));

        User user = new User(
            request.name().trim(),
            email,
            passwordEncoder.encode(rawPassword),
            request.phone() != null ? request.phone().trim() : null
        );

        if (request.status() != null && !request.status().isBlank()) {
            user.setStatus(request.status().toUpperCase().trim());
        }

        user.setRoles(new HashSet<>(Set.of(role)));
        user = userRepository.save(user);

        auditService.logEvent(
            principal != null ? principal.getUsername() : "System",
            "USER_CREATED",
            "USER",
            user.getUserId().toString(),
            user.getName(),
            "NONE",
            role.getName(),
            "User provisioned via Admin interface"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.fromEntity(user));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserDto> getUsers() {
        return userRepository.findAll().stream()
            .map(UserDto::fromEntity)
            .toList();
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<UserDto> updateStatus(
        @PathVariable UUID id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails principal
    ) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        String previousStatus = user.getStatus();
        String newStatus = body.get("status");
        if (newStatus != null && !newStatus.isBlank()) {
            user.setStatus(newStatus.toUpperCase().trim());
            userRepository.save(user);

            auditService.logEvent(
                principal != null ? principal.getUsername() : "Admin",
                "USER_STATUS_CHANGE",
                "USER",
                user.getUserId().toString(),
                user.getName(),
                previousStatus,
                user.getStatus(),
                body.getOrDefault("reason", "Administrative user status change")
            );
        }
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public ResponseEntity<UserDto> updateRole(
        @PathVariable UUID id,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails principal
    ) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        String previousRoles = user.getRoles() != null
            ? user.getRoles().stream().map(Role::getName).collect(Collectors.joining(", "))
            : "NONE";
        String roleName = body.get("role");
        if (roleName != null && !roleName.isBlank()) {
            String fullRoleName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
            Role role = roleRepository.findByName(fullRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + fullRoleName));
            user.setRoles(new HashSet<>(Set.of(role)));
            userRepository.save(user);

            auditService.logEvent(
                principal != null ? principal.getUsername() : "Admin",
                "ROLE_CHANGE",
                "USER",
                user.getUserId().toString(),
                user.getName(),
                previousRoles,
                fullRoleName,
                body.getOrDefault("reason", "Administrative role reassignment")
            );
        }
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public List<AuditLogItem> getAuditLogs() {
        List<AuditLogItem> items = new ArrayList<>(auditService.getRecentAuditLogs());

        // Merge historical status and transfer records for continuity
        statusHistoryRepository.findTop25ByOrderByCreatedAtDesc().forEach(h -> {
            String volunteerName = h.getVolunteer() != null && h.getVolunteer().getUser() != null
                ? h.getVolunteer().getUser().getName()
                : "Volunteer";
            String actor = h.getChangedBy() != null ? h.getChangedBy().getName() : "System";
            items.add(new AuditLogItem(
                h.getHistoryId(),
                "VOLUNTEER_STATUS",
                volunteerName + (h.getVolunteer() != null ? " (" + h.getVolunteer().getCollegeId() + ")" : ""),
                "STATUS_CHANGE",
                h.getFromStatus() != null ? h.getFromStatus() : "NONE",
                h.getToStatus(),
                h.getReason() != null ? h.getReason() : "Direct administrative update",
                actor,
                h.getCreatedAt()
            ));
        });

        transferHistoryRepository.findTop25ByOrderByTransferredAtDesc().forEach(t -> {
            String volunteerName = t.getVolunteer() != null && t.getVolunteer().getUser() != null
                ? t.getVolunteer().getUser().getName()
                : "Volunteer";
            String fromUnit = t.getFromUnit() != null ? t.getFromUnit().getUnitNumber() : "None";
            String toUnit = t.getToUnit() != null ? t.getToUnit().getUnitNumber() : "Unassigned";
            String actor = t.getAuthorizedBy() != null ? t.getAuthorizedBy().getName() : "Officer";
            items.add(new AuditLogItem(
                t.getTransferId(),
                "UNIT_TRANSFER",
                volunteerName,
                "TRANSFER",
                fromUnit,
                toUnit,
                t.getReason() != null ? t.getReason() : "Administrative transfer",
                actor,
                t.getTransferredAt()
            ));
        });

        return items.stream()
            .distinct()
            .sorted(Comparator.comparing(AuditLogItem::timestamp).reversed())
            .limit(100)
            .toList();
    }

    private String generateSecurePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
