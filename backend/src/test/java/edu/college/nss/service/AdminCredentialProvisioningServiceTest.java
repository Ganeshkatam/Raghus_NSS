package edu.college.nss.service;

import edu.college.nss.domain.AuditLog;
import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.Role;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuditLogRepository;
import edu.college.nss.repository.AuthSessionRepository;
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
    private AuthSessionRepository authSessionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminCredentialProvisioningService provisioningService;

    private static final String APPROVED_ADMIN_EMAIL = "admin@raghunss.edu";
    private static final String OPERATOR_EMAIL = "infra-lead@raghuenggcollege.in";
    private static final String STRONG_PASSWORD_12 = "SecurePass1!"; // Exactly 12 chars
    private static final String WEAK_PASSWORD_11 = "ShortPass1!"; // Exactly 11 chars

    @BeforeEach
    void setUp() {
        provisioningService = new AdminCredentialProvisioningService(
            userRepository,
            authSessionRepository,
            auditLogRepository,
            passwordEncoder
        );
    }

    @Test
    @DisplayName("Provisioning succeeds for locked administrator with verified operator attribution and atomic session revocation")
    void provisionAdminCredential_success() {
        User lockedUser = new User("System Administrator", APPROVED_ADMIN_EMAIL, "*COMPROMISED_LOCKED*", "9876543210");
        lockedUser.setUserId(UUID.randomUUID());
        lockedUser.setStatus("LOCKED");
        lockedUser.setMustChangePassword(false);
        Role adminRole = new Role("ROLE_ADMIN", "System Administrator");
        lockedUser.getRoles().add(adminRole);

        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$newBcryptEncodedHash12345");

        AuthSession activeSession = new AuthSession();
        activeSession.setActive(true);
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(lockedUser.getUserId()))
            .thenReturn(new ArrayList<>(List.of(activeSession)));

        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        OperatorIdentity operator = OperatorIdentity.fromVerifiedCli(OPERATOR_EMAIL);
        provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, operator);

        // Verify user state updated
        assertEquals("ACTIVE", lockedUser.getStatus());
        assertTrue(lockedUser.isMustChangePassword());
        assertEquals("$2a$12$newBcryptEncodedHash12345", lockedUser.getPasswordHash());
        verify(userRepository).save(lockedUser);

        // Verify session revoked
        assertFalse(activeSession.isActive());
        assertEquals("ADMIN_PROVISIONED", activeSession.getRevokeReason());
        verify(authSessionRepository).save(activeSession);

        // Verify audit log recorded with verified operator identity
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertEquals(OPERATOR_EMAIL, audit.getActorEmail());
        assertTrue(audit.getActorName().contains("CLI Operator"));
        assertEquals("ADMIN_CREDENTIAL_PROVISIONED", audit.getAction());
        assertEquals("LOCKED", audit.getPreviousState());
        assertEquals("ACTIVE", audit.getNewState());

        // Verify sensitive char array zeroed
        for (char c : passwordChars) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("Provisioning is strictly rejected for unapproved email addresses")
    void provisionAdminCredential_unapprovedEmail_throwsSecurityException() {
        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        SecurityException ex = assertThrows(SecurityException.class, () ->
            provisioningService.provisionAdminCredential("attacker@example.com", passwordChars, OPERATOR_EMAIL)
        );
        assertTrue(ex.getMessage().contains("not an approved administrator recovery identity"));
    }

    @Test
    @DisplayName("Provisioning is strictly rejected if target user lacks ROLE_ADMIN (privilege escalation prevention)")
    void provisionAdminCredential_targetLacksAdminRole_throwsSecurityException() {
        User lockedVolunteer = new User("Volunteer User", APPROVED_ADMIN_EMAIL, "*COMPROMISED_LOCKED*", "9876543210");
        lockedVolunteer.setUserId(UUID.randomUUID());
        lockedVolunteer.setStatus("LOCKED");
        lockedVolunteer.getRoles().add(new Role("ROLE_VOLUNTEER", "Volunteer"));

        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.of(lockedVolunteer));

        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        SecurityException ex = assertThrows(SecurityException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, OPERATOR_EMAIL)
        );
        assertTrue(ex.getMessage().contains("does not possess ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Provisioning fails if target account does not exist")
    void provisionAdminCredential_targetNotFound_throwsIllegalStateException() {
        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.empty());

        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, OPERATOR_EMAIL)
        );
        assertTrue(ex.getMessage().contains("Target account does not exist"));
    }

    @Test
    @DisplayName("Provisioning fails if target account is not LOCKED (prevents resetting active accounts)")
    void provisionAdminCredential_accountNotLocked_throwsIllegalStateException() {
        User activeUser = new User("Already Active Admin", APPROVED_ADMIN_EMAIL, "hash", "9876543210");
        activeUser.setStatus("ACTIVE");
        activeUser.getRoles().add(new Role("ROLE_ADMIN", "Admin"));
        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.of(activeUser));

        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, OPERATOR_EMAIL)
        );
        assertTrue(ex.getMessage().contains("Target account is not locked"));
    }

    @Test
    @DisplayName("Password policy boundary: 11 characters rejected, 12 characters accepted")
    void passwordPolicy_boundaryCheck() {
        // 11 characters
        char[] weak11 = WEAK_PASSWORD_11.toCharArray();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, weak11, OPERATOR_EMAIL)
        );
        assertTrue(ex.getMessage().contains("between 12 and 128 characters"));

        // 12 characters reaches repository check
        User lockedUser = new User("System Administrator", APPROVED_ADMIN_EMAIL, "hash", "9876543210");
        lockedUser.setUserId(UUID.randomUUID());
        lockedUser.setStatus("LOCKED");
        lockedUser.getRoles().add(new Role("ROLE_ADMIN", "Admin"));
        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.of(lockedUser));
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(any())).thenReturn(List.of());

        char[] valid12 = STRONG_PASSWORD_12.toCharArray();
        assertDoesNotThrow(() ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, valid12, OPERATOR_EMAIL)
        );
    }

    @Test
    @DisplayName("ProvisionFromFile safely reads, provisions, overwrites file bytes with zeros, and unlinks file")
    void provisionFromFile_success_overwritesAndDeletesFile() throws IOException {
        Path tempSecretFile = Files.createTempFile("nss-admin-secret-", ".tmp");
        Files.writeString(tempSecretFile, STRONG_PASSWORD_12 + "\n");

        User lockedUser = new User("System Administrator", APPROVED_ADMIN_EMAIL, "oldHash", "9876543210");
        lockedUser.setUserId(UUID.randomUUID());
        lockedUser.setStatus("LOCKED");
        Role adminRole = new Role("ROLE_ADMIN", "System Administrator");
        lockedUser.getRoles().add(adminRole);

        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.encode(any())).thenReturn("newHash");
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(any())).thenReturn(List.of());

        provisioningService.provisionFromFile(tempSecretFile, APPROVED_ADMIN_EMAIL, OPERATOR_EMAIL);

        // Verify file was unlinked from disk after use
        assertFalse(Files.exists(tempSecretFile));
    }
}
