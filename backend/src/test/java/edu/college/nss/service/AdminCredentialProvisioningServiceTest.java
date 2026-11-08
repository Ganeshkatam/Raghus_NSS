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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

    private TransactionTemplate transactionTemplate;
    private AdminCredentialProvisioningService provisioningService;

    private static final String APPROVED_ADMIN_EMAIL = "admin@raghunss.edu";
    private static final String STRONG_PASSWORD_12 = "SecurePass1!"; // Exactly 12 chars
    private static final String WEAK_PASSWORD_11 = "ShortPass1!"; // Exactly 11 chars
    private static final String TEST_OPERATOR_KEY = "test-operator-key-auth-xyz987";

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() { return new Object(); }
            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {}
            @Override
            protected void doCommit(DefaultTransactionStatus status) {}
            @Override
            protected void doRollback(DefaultTransactionStatus status) {}
        });

        provisioningService = new AdminCredentialProvisioningService(
            userRepository,
            authSessionRepository,
            auditLogRepository,
            passwordEncoder,
            transactionTemplate,
            TEST_OPERATOR_KEY
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
        OperatorIdentity operator = OperatorIdentity.fromSystemProcess();
        provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, operator, TEST_OPERATOR_KEY);

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
        assertEquals(operator.identifier(), audit.getActorEmail());
        assertEquals(operator.displayName(), audit.getActorName());
        assertEquals("ADMIN_CREDENTIAL_PROVISIONED", audit.getAction());
        assertEquals("LOCKED", audit.getPreviousState());
        assertEquals("ACTIVE", audit.getNewState());

        // Verify sensitive char array zeroed
        for (char c : passwordChars) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("Provisioning without operator key fails closed")
    void provisionAdminCredential_missingKey_failsClosed() {
        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        SecurityException ex = assertThrows(SecurityException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars)
        );
        assertTrue(ex.getMessage().contains("No operator recovery key was provided"));
    }

    @Test
    @DisplayName("Provisioning is strictly rejected for unapproved email addresses")
    void provisionAdminCredential_unapprovedEmail_throwsSecurityException() {
        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        SecurityException ex = assertThrows(SecurityException.class, () ->
            provisioningService.provisionAdminCredential("attacker@example.com", passwordChars, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
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
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
        );
        assertTrue(ex.getMessage().contains("does not possess ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Provisioning fails if target account does not exist")
    void provisionAdminCredential_targetNotFound_throwsIllegalStateException() {
        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.empty());

        char[] passwordChars = STRONG_PASSWORD_12.toCharArray();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
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
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, passwordChars, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
        );
        assertTrue(ex.getMessage().contains("Target account is not locked"));
    }

    @Test
    @DisplayName("Password policy boundary: 11 characters rejected, 12 characters accepted")
    void passwordPolicy_boundaryCheck() {
        // 11 characters
        char[] weak11 = WEAK_PASSWORD_11.toCharArray();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, weak11, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
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
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, valid12, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
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

        provisioningService.provisionFromFile(tempSecretFile, APPROVED_ADMIN_EMAIL, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY);

        // Verify file was unlinked from disk after use
        assertFalse(Files.exists(tempSecretFile));
    }

    @Test
    @DisplayName("ProvisionFromFile guarantees zero-byte overwrite and deletion even when provisioning fails")
    void provisionFromFile_failure_stillOverwritesAndDeletesFile() throws IOException {
        Path tempSecretFile = Files.createTempFile("nss-admin-secret-fail-", ".tmp");
        Files.writeString(tempSecretFile, WEAK_PASSWORD_11 + "\n");

        assertThrows(IllegalArgumentException.class, () ->
            provisioningService.provisionFromFile(tempSecretFile, APPROVED_ADMIN_EMAIL, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
        );

        // Verify file was cleaned up and unlinked from disk despite failure
        assertFalse(Files.exists(tempSecretFile));
    }

    @Test
    @DisplayName("Provisioning succeeds when role name is 'ADMIN' without 'ROLE_' prefix")
    void provisionAdminCredential_successWithAdminRoleWithoutPrefix() {
        User lockedUser = new User("System Administrator", APPROVED_ADMIN_EMAIL, "hash", "9876543210");
        lockedUser.setUserId(UUID.randomUUID());
        lockedUser.setStatus("LOCKED");
        lockedUser.getRoles().add(new Role("ADMIN", "System Administrator"));

        when(userRepository.findByEmailWithLock(APPROVED_ADMIN_EMAIL)).thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.encode(any())).thenReturn("newBcryptHash");
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(any())).thenReturn(List.of());

        char[] valid12 = STRONG_PASSWORD_12.toCharArray();
        assertDoesNotThrow(() ->
            provisioningService.provisionAdminCredential(APPROVED_ADMIN_EMAIL, valid12, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
        );

        assertEquals("ACTIVE", lockedUser.getStatus());
        assertTrue(lockedUser.isMustChangePassword());
        assertEquals("newBcryptHash", lockedUser.getPasswordHash());
    }

    @Test
    @DisplayName("Provisioning succeeds for production administrator target with matching UUID and ADMIN role")
    void provisionAdminCredential_productionAdminTarget_successWithVerifiedUuid() {
        String prodEmail = AdminCredentialProvisioningService.PRODUCTION_ADMIN_EMAIL;
        UUID prodUuid = AdminCredentialProvisioningService.PRODUCTION_ADMIN_UUID;

        User lockedAdmin = new User("Production Admin", prodEmail, "oldHash", "9876543210");
        lockedAdmin.setUserId(prodUuid);
        lockedAdmin.setStatus("LOCKED");
        lockedAdmin.getRoles().add(new Role("ADMIN", "System Administrator"));

        when(userRepository.findByEmailWithLock(prodEmail)).thenReturn(Optional.of(lockedAdmin));
        when(passwordEncoder.encode(any())).thenReturn("encodedHash");
        when(authSessionRepository.findByUser_UserIdAndIsActiveTrue(prodUuid)).thenReturn(List.of());

        char[] valid12 = STRONG_PASSWORD_12.toCharArray();
        assertDoesNotThrow(() ->
            provisioningService.provisionAdminCredential(prodEmail, valid12, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
        );

        assertEquals("ACTIVE", lockedAdmin.getStatus());
        assertTrue(lockedAdmin.isMustChangePassword());
    }

    @Test
    @DisplayName("Provisioning fails for production administrator target if UUID does not match pinned production identity")
    void provisionAdminCredential_productionAdminTarget_failsOnMismatchedUuid() {
        String prodEmail = AdminCredentialProvisioningService.PRODUCTION_ADMIN_EMAIL;
        UUID wrongUuid = UUID.randomUUID();

        User spoofedAdmin = new User("Spoofed Admin", prodEmail, "oldHash", "9876543210");
        spoofedAdmin.setUserId(wrongUuid);
        spoofedAdmin.setStatus("LOCKED");
        spoofedAdmin.getRoles().add(new Role("ADMIN", "System Administrator"));

        when(userRepository.findByEmailWithLock(prodEmail)).thenReturn(Optional.of(spoofedAdmin));

        char[] valid12 = STRONG_PASSWORD_12.toCharArray();
        SecurityException ex = assertThrows(SecurityException.class, () ->
            provisioningService.provisionAdminCredential(prodEmail, valid12, OperatorIdentity.fromSystemProcess(), TEST_OPERATOR_KEY)
        );
        assertTrue(ex.getMessage().contains("UUID mismatch"));
    }

    @Test
    @DisplayName("Operator recovery key verification rejects missing, blank, or incorrect keys and fails closed")
    void verifyOperatorAuthorization_checksKeyCorrectness() {
        // Missing key (null)
        SecurityException exNull = assertThrows(SecurityException.class, () ->
            provisioningService.verifyOperatorAuthorization(null)
        );
        assertTrue(exNull.getMessage().contains("No operator recovery key was provided"));

        // Blank key
        SecurityException exBlank = assertThrows(SecurityException.class, () ->
            provisioningService.verifyOperatorAuthorization("   ")
        );
        assertTrue(exBlank.getMessage().contains("No operator recovery key was provided"));

        // Wrong key
        SecurityException exWrong = assertThrows(SecurityException.class, () ->
            provisioningService.verifyOperatorAuthorization("incorrect-key-token")
        );
        assertTrue(exWrong.getMessage().contains("Invalid OPERATOR_RECOVERY_KEY provided"));

        // Valid key
        assertDoesNotThrow(() -> provisioningService.verifyOperatorAuthorization(TEST_OPERATOR_KEY));

        // When service is constructed without an expected key, it must fail closed
        AdminCredentialProvisioningService unconfiguredService = new AdminCredentialProvisioningService(
            userRepository, authSessionRepository, auditLogRepository, passwordEncoder, transactionTemplate, null
        );
        SecurityException exUnconfigured = assertThrows(SecurityException.class, () ->
            unconfiguredService.verifyOperatorAuthorization(TEST_OPERATOR_KEY)
        );
        assertTrue(exUnconfigured.getMessage().contains("OPERATOR_RECOVERY_KEY is not configured. Administrator credential recovery is fail-closed."));
    }
}
