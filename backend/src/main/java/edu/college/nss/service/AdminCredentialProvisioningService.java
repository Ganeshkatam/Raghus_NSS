package edu.college.nss.service;

import edu.college.nss.domain.AuditLog;
import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.Role;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuditLogRepository;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.RoleRepository;
import edu.college.nss.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AdminCredentialProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AdminCredentialProvisioningService.class);

    private static final Pattern PASSWORD_COMPLEXITY_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/]).{12,}$"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCredentialProvisioningService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        AuthSessionRepository authSessionRepository,
        AuditLogRepository auditLogRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authSessionRepository = authSessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Executes atomic, operator-authorized administrator credential provisioning.
     *
     * @param targetEmail The administrator account email to provision
     * @param newPassword The new temporary password character array (zeroed after processing)
     * @param operatorIdentity Identity of the authorized deploying operator
     * @throws IllegalStateException if account is missing, not locked, or operator identity is missing
     * @throws IllegalArgumentException if password fails security complexity requirements
     */
    @Transactional
    public void provisionAdminCredential(String targetEmail, char[] newPassword, String operatorIdentity) {
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new IllegalArgumentException("Target administrator email is required.");
        }
        if (operatorIdentity == null || operatorIdentity.isBlank()) {
            throw new IllegalStateException("Authorized operator identity is mandatory for credential provisioning.");
        }
        if (newPassword == null || newPassword.length == 0) {
            throw new IllegalArgumentException("New password cannot be empty.");
        }

        String passwordString = new String(newPassword);
        try {
            validatePasswordComplexity(passwordString);

            // 1. Lock the user row with pessimistic write lock to prevent race conditions
            User user = userRepository.findByEmailWithLock(targetEmail.toLowerCase().trim())
                .orElseThrow(() -> new IllegalStateException(
                    "Target account does not exist. Automatic account creation is prohibited; an existing administrator identity must be present."
                ));

            // 2. Verify account is locked and eligible for provisioning
            if (!"LOCKED".equalsIgnoreCase(user.getStatus())) {
                throw new IllegalStateException(
                    "Target account is not locked (current status=" + user.getStatus() + "). Provisioning can only be performed on locked accounts."
                );
            }

            // 3. Ensure ROLE_ADMIN is present
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN definition not found in database."));
            if (!user.getRoles().contains(adminRole)) {
                user.getRoles().add(adminRole);
            }

            // 4. Update credentials and enforce forced password change
            user.setPasswordHash(passwordEncoder.encode(passwordString));
            user.setStatus("ACTIVE");
            user.setMustChangePassword(true);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            // 5. Revoke all existing sessions for this identity
            List<AuthSession> existingSessions = authSessionRepository.findByUser_UserIdAndIsActiveTrue(user.getUserId());
            for (AuthSession session : existingSessions) {
                session.revoke("ADMIN_PROVISIONED");
                authSessionRepository.save(session);
            }

            // 6. Record structured audit log entry (attributable to operator; never logging password or hash)
            AuditLog auditLog = new AuditLog();
            auditLog.setActorName("Authorized Operator");
            auditLog.setActorEmail(operatorIdentity.trim());
            auditLog.setAction("ADMIN_CREDENTIAL_PROVISIONED");
            auditLog.setEntityType("USER");
            auditLog.setEntityId(user.getUserId().toString());
            auditLog.setEntityName(user.getName());
            auditLog.setPreviousState("LOCKED");
            auditLog.setNewState("ACTIVE");
            auditLog.setReason("Controlled administrator credential rotation with forced password change.");
            auditLogRepository.save(auditLog);

            log.info("Administrator credentials successfully provisioned for user ID {} by operator {}",
                user.getUserId(), operatorIdentity);
        } finally {
            // Securely wipe sensitive in-memory password data
            Arrays.fill(newPassword, '\0');
        }
    }

    /**
     * Reads credentials from a protected local secret file, enforces file safety,
     * provisions the account, and securely deletes the file.
     */
    public void provisionFromFile(Path secretFilePath, String targetEmail, String operatorIdentity) throws IOException {
        if (Files.isSymbolicLink(secretFilePath)) {
            throw new IllegalArgumentException("Symbolic links are strictly prohibited for secret file paths.");
        }
        if (!Files.isRegularFile(secretFilePath)) {
            throw new IllegalArgumentException("Secret file path must be a regular file: " + secretFilePath);
        }
        if (Files.size(secretFilePath) > 4096) {
            throw new IllegalArgumentException("Secret file exceeds maximum permitted size (4KB).");
        }

        String content = Files.readString(secretFilePath).trim();
        char[] passwordChars = content.toCharArray();

        try {
            provisionAdminCredential(targetEmail, passwordChars, operatorIdentity);
        } finally {
            // Securely delete temporary secret file after use
            try {
                Files.deleteIfExists(secretFilePath);
            } catch (IOException ex) {
                log.warn("Failed to delete secret file after provisioning: {}", ex.getMessage());
            }
        }
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || !PASSWORD_COMPLEXITY_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                "Password must be at least 12 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character."
            );
        }
    }
}
