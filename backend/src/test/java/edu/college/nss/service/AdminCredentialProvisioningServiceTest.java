package edu.college.nss.service;

import edu.college.nss.domain.AuditLog;
import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.Role;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuditLogRepository;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.RoleRepository;
import edu.college.nss.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminCredentialProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminCredentialProvisioningService provisioningService;

    private static final String ADMIN_EMAIL = "admin@raghunss.edu";
    private static final String OPERATOR_ID = "infra-lead@raghuenggcollege.in";
    private static final String STRONG_PASSWORD = "SuperSecurePassword2026!";

    @BeforeEach
    void setUp() {
        provisioningService = new AdminCredentialProvisioningService(
            userRepository,
            roleRepository,
            authSessionRepository,
            auditLogRepository,
            passwordEncoder
        );
    }

    @Test
    @DisplayName("Provisioning succeeds for locked administrator with atomic updates, session revocation, and audit logging")
    void provisionAdminCredential_success() {
        User lockedUser = new User("System Administrator", ADMIN_EMAIL, "*COMPROMISED_LOCKED*", "9876543210");
        lockedUser.setUserId(UUID.randomUUID());
        lockedUser.setStatus("LOCKED");
        lockedUser.setMustChangePassword(false);

        Role adminRole = new Role("ROLE_ADMIN", "System Administrator");
        when(userRepository.findByEmailWithLock(ADMIN_EMAIL)).thenReturn(Optional.of(lockedUser));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("$2a$12$newBcryptEncodedHash12345");

        AuthSession activeSession = new AuthSession();
        activeSession.setActive(true);
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(lockedUser.getUserId()))
            .thenReturn(new ArrayList<>(List.of(activeSession)));

        char[] passwordChars = STRONG_PASSWORD.toCharArray();
        provisioningService.provisionAdminCredential(ADMIN_EMAIL, passwordChars, OPERATOR_ID);

        // Verify user state updated
        assertEquals("ACTIVE", lockedUser.getStatus());
        assertTrue(lockedUser.isMustChangePassword());
        assertEquals("$2a$12$newBcryptEncodedHash12345", lockedUser.getPasswordHash());
        assertTrue(lockedUser.getRoles().contains(adminRole));
        verify(userRepository).save(lockedUser);

        // Verify session revoked
        assertFalse(activeSession.isActive());
        assertEquals("ADMIN_PROVISIONED", activeSession.getRevokeReason());
        verify(authSessionRepository).save(activeSession);

        // Verify audit log recorded with operator identity
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertEquals(OPERATOR_ID, audit.getActorEmail());
        assertEquals("ADMIN_CREDENTIAL_PROVISIONED", audit.getAction());
        assertEquals("LOCKED", audit.getPreviousState());
        assertEquals("ACTIVE", audit.getNewState());

        // Verify sensitive char array zeroed
        for (char c : passwordChars) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("Provisioning fails if target account does not exist")
    void provisionAdminCredential_targetNotFound_throwsIllegalStateException() {
        when(userRepository.findByEmailWithLock("nonexistent@raghunss.edu")).thenReturn(Optional.empty());

        char[] passwordChars = STRONG_PASSWORD.toCharArray();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            provisioningService.provisionAdminCredential("nonexistent@raghunss.edu", passwordChars, OPERATOR_ID)
        );
        assertTrue(ex.getMessage().contains("Target account does not exist"));
    }

    @Test
    @DisplayName("Provisioning fails if target account is not LOCKED (prevents resetting active accounts)")
    void provisionAdminCredential_accountNotLocked_throwsIllegalStateException() {
        User activeUser = new User("Already Active Admin", ADMIN_EMAIL, "hash", "9876543210");
        activeUser.setStatus("ACTIVE");
        when(userRepository.findByEmailWithLock(ADMIN_EMAIL)).thenReturn(Optional.of(activeUser));

        char[] passwordChars = STRONG_PASSWORD.toCharArray();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            provisioningService.provisionAdminCredential(ADMIN_EMAIL, passwordChars, OPERATOR_ID)
        );
        assertTrue(ex.getMessage().contains("Target account is not locked"));
    }

    @Test
    @DisplayName("Provisioning fails if password does not satisfy complexity requirements")
    void provisionAdminCredential_weakPassword_throwsIllegalArgumentException() {
        char[] weakPassword = "short".toCharArray();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            provisioningService.provisionAdminCredential(ADMIN_EMAIL, weakPassword, OPERATOR_ID)
        );
        assertTrue(ex.getMessage().contains("at least 12 characters"));
    }

    @Test
    @DisplayName("Provisioning fails if operator identity is missing")
    void provisionAdminCredential_missingOperator_throwsIllegalStateException() {
        char[] passwordChars = STRONG_PASSWORD.toCharArray();
        assertThrows(IllegalStateException.class, () ->
            provisioningService.provisionAdminCredential(ADMIN_EMAIL, passwordChars, "   ")
        );
    }

    @Test
    @DisplayName("ProvisionFromFile safely reads, provisions, and securely deletes the secret file")
    void provisionFromFile_success_deletesFile() throws IOException {
        Path tempSecretFile = Files.createTempFile("nss-admin-secret-", ".tmp");
        Files.writeString(tempSecretFile, STRONG_PASSWORD);

        User lockedUser = new User("System Administrator", ADMIN_EMAIL, "oldHash", "9876543210");
        lockedUser.setUserId(UUID.randomUUID());
        lockedUser.setStatus("LOCKED");
        Role adminRole = new Role("ROLE_ADMIN", "System Administrator");

        when(userRepository.findByEmailWithLock(ADMIN_EMAIL)).thenReturn(Optional.of(lockedUser));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("newHash");
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(any())).thenReturn(List.of());

        provisioningService.provisionFromFile(tempSecretFile, ADMIN_EMAIL, OPERATOR_ID);

        // Verify file was securely deleted after use
        assertFalse(Files.exists(tempSecretFile));
    }
}
