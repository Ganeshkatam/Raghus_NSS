package edu.college.nss.web;

import edu.college.nss.domain.Role;
import edu.college.nss.domain.User;
import edu.college.nss.repository.RoleRepository;
import edu.college.nss.repository.UnitTransferHistoryRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.repository.VolunteerStatusHistoryRepository;
import edu.college.nss.web.dto.AuditLogItem;
import edu.college.nss.web.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VolunteerStatusHistoryRepository statusHistoryRepository;
    private final UnitTransferHistoryRepository transferHistoryRepository;

    public UserController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          VolunteerStatusHistoryRepository statusHistoryRepository,
                          UnitTransferHistoryRepository transferHistoryRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.transferHistoryRepository = transferHistoryRepository;
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
    public ResponseEntity<UserDto> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        String newStatus = body.get("status");
        if (newStatus != null && !newStatus.isBlank()) {
            user.setStatus(newStatus.toUpperCase());
            userRepository.save(user);
        }
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public ResponseEntity<UserDto> updateRole(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        String roleName = body.get("role");
        if (roleName != null && !roleName.isBlank()) {
            String fullRoleName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
            Role role = roleRepository.findByName(fullRoleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + fullRoleName));
            user.setRoles(new HashSet<>(Set.of(role)));
            userRepository.save(user);
        }
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public List<AuditLogItem> getAuditLogs() {
        List<AuditLogItem> items = new ArrayList<>();

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

        items.sort(Comparator.comparing(AuditLogItem::timestamp).reversed());
        return items;
    }
}
