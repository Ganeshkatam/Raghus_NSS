package edu.college.nss.web.dto;

import edu.college.nss.domain.User;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserDto(
    UUID userId,
    String name,
    String email,
    String phone,
    String status,
    Set<String> roles,
    Set<String> permissions
) {
    public UserDto(UUID userId, String name, String email, String phone, String status, Set<String> roles) {
        this(userId, name, email, phone, status, roles, Set.of());
    }

    public static UserDto fromEntity(User user) {
        Set<String> roleNames = user.getRoles() != null
            ? user.getRoles().stream()
                .map(r -> r.getName().startsWith("ROLE_") ? r.getName().substring(5) : r.getName())
                .collect(Collectors.toSet())
            : Set.of();

        Set<String> permissionNames = new HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                if (role.getPermissions() != null) {
                    role.getPermissions().forEach(p -> permissionNames.add(p.getName()));
                }
            });
        }

        return new UserDto(
            user.getUserId(),
            user.getName(),
            user.getEmail(),
            user.getPhone(),
            user.getStatus(),
            roleNames,
            permissionNames
        );
    }
}
