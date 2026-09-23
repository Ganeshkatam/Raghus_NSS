package edu.college.nss.web.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInMs,
    UserDto user
) {
    public AuthResponse(String accessToken, String refreshToken, long expiresInMs, UserDto user) {
        this(accessToken, refreshToken, "Bearer", expiresInMs, user);
    }
}
