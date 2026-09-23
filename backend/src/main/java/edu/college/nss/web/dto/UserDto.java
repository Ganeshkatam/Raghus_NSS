package edu.college.nss.web.dto;

import edu.college.nss.domain.User;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserDto(
    UUID userId,
    String name,
    String email,
    String phone,
    String status,
    Set<String> roles
) {
    public static UserDto fromEntity(User user) {
        Set<String> roleNames = user.getRoles() != null
            ? user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet())
            : Set.of();

        return new UserDto(
            user.getUserId(),
            user.getName(),
            user.getEmail(),
            user.getPhone(),
            user.getStatus(),
            roleNames
        );
    }
}
