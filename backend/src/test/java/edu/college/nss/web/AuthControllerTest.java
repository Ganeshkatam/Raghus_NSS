package edu.college.nss.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.college.nss.domain.Role;
import edu.college.nss.domain.User;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.security.CustomUserDetails;
import edu.college.nss.security.JwtTokenProvider;
import edu.college.nss.service.AuthService;
import edu.college.nss.web.dto.AuthResponse;
import edu.college.nss.web.dto.LoginRequest;
import edu.college.nss.web.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtTokenProvider tokenProvider;

    @Test
    public void apiRoot_shouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ready"))
            .andExpect(jsonPath("$.service").value("nss-backend"));
    }

    @Test
    public void login_withValidCredentials_shouldReturnAuthResponse() throws Exception {
        UserDto userDto = new UserDto(UUID.randomUUID(), "Admin User", "admin@raghunss.edu", "1234567890", "ACTIVE", Set.of("ROLE_ADMIN"));
        AuthResponse authResponse = new AuthResponse("mock-access-token", "mock-refresh-token", 900000L, userDto);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        LoginRequest request = new LoginRequest("admin@raghunss.edu", "Admin@Password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token"))
            .andExpect(jsonPath("$.user.email").value("admin@raghunss.edu"));
    }

    @Test
    public void login_withInvalidCredentials_shouldReturnStructuredError() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("admin@raghunss.edu", "WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    public void protectedEndpoint_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }
}
