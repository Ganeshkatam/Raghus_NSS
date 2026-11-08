package edu.college.nss.service;

import edu.college.nss.domain.AuditLog;
import edu.college.nss.domain.AuthSession;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuditLogRepository;
import edu.college.nss.repository.AuthSessionRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminCredentialProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AdminCredentialProvisioningService.class);

    public static final String PRODUCTION_ADMIN_EMAIL = "katamganesh61@gmail.com";
    public static final UUID PRODUCTION_ADMIN_UUID = UUID.fromString("50875b8e-e462-4891-90b6-027fb8572ac3");

    public static final Set<String> APPROVED_ADMIN_EMAILS = Set.of(
        PRODUCTION_ADMIN_EMAIL,
        "admin@raghunss.edu"
    );

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String configuredOperatorKey;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminCredentialProvisioningService(
        UserRepository userRepository,
        AuthSessionRepository authSessionRepository,
        AuditLogRepository auditLogRepository,
        PasswordEncoder passwordEncoder,
        TransactionTemplate transactionTemplate
    ) {
        this(userRepository, authSessionRepository, auditLogRepository, passwordEncoder, transactionTemplate, System.getenv("OPERATOR_RECOVERY_KEY"));
    }

    public AdminCredentialProvisioningService(
        UserRepository userRepository,
        AuthSessionRepository authSessionRepository,
        AuditLogRepository auditLogRepository,
        PasswordEncoder passwordEncoder,
        TransactionTemplate transactionTemplate,
        String configuredOperatorKey
    ) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.configuredOperatorKey = configuredOperatorKey;
    }

    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        String key = this.configuredOperatorKey != null && !this.configuredOperatorKey.isBlank()
            ? this.configuredOperatorKey
            : System.getenv("OPERATOR_RECOVERY_KEY");
        if (key != null && !key.isBlank()) {
            log.info("AdminCredentialProvisioningService: OPERATOR_RECOVERY_KEY is active and verified configured.");
        } else {
            log.warn("AdminCredentialProvisioningService: OPERATOR_RECOVERY_KEY is not configured. Administrator recovery operations will fail closed.");
        }
    }

    /**
     * Reads a password securely from the interactive system console.
     */
    public char[] readPasswordFromConsole(String prompt) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("System console is unavailable for interactive password entry.");
        }
        char[] password = console.readPassword(prompt != null ? prompt : "Enter temporary administrator password: ");
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password entered cannot be empty.");
        }
        return password;
    }

    /**
     * Verifies the operator recovery authorization token.
     * Enforces strict fail-closed authorization:
     * 1. The service MUST have a non-blank expected recovery key configured.
     * 2. The caller MUST supply a non-blank operator recovery key.
     * 3. Comparison is executed in constant time via MessageDigest.isEqual to prevent timing attacks.
     */
    public void verifyOperatorAuthorization(String operatorRecoveryKey) {
        String expectedKey = this.configuredOperatorKey != null && !this.configuredOperatorKey.isBlank()
            ? this.configuredOperatorKey
            : System.getenv("OPERATOR_RECOVERY_KEY");

        if (expectedKey == null || expectedKey.isBlank()) {
            throw new SecurityException("OPERATOR_RECOVERY_KEY is not configured. Administrator credential recovery is fail-closed.");
        }
        if (operatorRecoveryKey == null || operatorRecoveryKey.isBlank()) {
            throw new SecurityException("No operator recovery key was provided for administrator credential recovery.");
        }

        byte[] expected = expectedKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = operatorRecoveryKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new SecurityException("Invalid OPERATOR_RECOVERY_KEY provided. Access denied.");
        }
    }

    public void provisionAdminCredential(String targetEmail, char[] newPassword) {
        provisionAdminCredential(targetEmail, newPassword, OperatorIdentity.resolveCurrent(), null);
    }

    public void provisionAdminCredential(String targetEmail, char[] newPassword, OperatorIdentity operator) {
        provisionAdminCredential(targetEmail, newPassword, operator, null);
    }

    /**
     * Executes atomic, operator-authorized administrator credential provisioning.
     * Uses explicit TransactionTemplate to ensure database transaction boundaries
     * are guaranteed even when self-invoked or called outside AOP proxies.
     *
     * @param targetEmail The administrator account email to provision
     * @param newPassword The new temporary password character array (zeroed in finally block)
     * @param operator Trusted identity of the executing operator
     * @param operatorRecoveryKey Optional recovery key verified against OPERATOR_RECOVERY_KEY
     * @throws SecurityException if target email is not approved or target lacks ROLE_ADMIN/ADMIN
     * @throws IllegalStateException if account is missing or not locked
     * @throws IllegalArgumentException if password fails policy requirements
     */
    public void provisionAdminCredential(String targetEmail, char[] newPassword, OperatorIdentity operator, String operatorRecoveryKey) {
        verifyOperatorAuthorization(operatorRecoveryKey);
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new IllegalArgumentException("Target administrator email is required.");
        }
        if (newPassword == null || newPassword.length == 0) {
            throw new IllegalArgumentException("New password cannot be empty.");
        }

        OperatorIdentity effectiveOperator = operator != null ? operator : OperatorIdentity.resolveCurrent();
        String normalizedEmail = targetEmail.trim().toLowerCase();

        // 1. Target restriction: enforce strictly approved administrator recovery identities
        if (!APPROVED_ADMIN_EMAILS.contains(normalizedEmail)) {
            throw new SecurityException("Target email '" + normalizedEmail + "' is not an approved administrator recovery identity.");
        }

        try {
            // 2. Validate password complexity using policy before opening transaction
            PasswordPolicy.validate(newPassword);

            // 3. Execute atomic persistence and session revocation inside an explicit transaction
            transactionTemplate.executeWithoutResult(status -> {
                // Lock user row with pessimistic write lock to prevent race conditions
                User user = userRepository.findByEmailWithLock(normalizedEmail)
                    .orElseThrow(() -> new IllegalStateException(
                        "Target account does not exist. Automatic account creation is prohibited; an existing administrator identity must be present."
                    ));

                // Target UUID verification for production administrator identity
                if (PRODUCTION_ADMIN_EMAIL.equalsIgnoreCase(normalizedEmail) && !PRODUCTION_ADMIN_UUID.equals(user.getUserId())) {
                    throw new SecurityException("Target user UUID mismatch for " + PRODUCTION_ADMIN_EMAIL + ". Expected " + PRODUCTION_ADMIN_UUID + ", but found " + user.getUserId());
                }

                // Verify account is currently LOCKED
                if (!"LOCKED".equalsIgnoreCase(user.getStatus())) {
                    throw new IllegalStateException(
                        "Target account is not locked (current status=" + user.getStatus() + "). Provisioning can only be performed on locked accounts."
                    );
                }

                // Verify account is ALREADY an administrator (accepting either ROLE_ADMIN or ADMIN)
                boolean hasAdminRole = user.getRoles() != null && user.getRoles().stream()
                    .anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName()) || "ADMIN".equalsIgnoreCase(r.getName()));
                if (!hasAdminRole) {
                    throw new SecurityException("Target account does not possess ROLE_ADMIN or ADMIN. Promoting non-administrator accounts is strictly prohibited.");
                }

                // Update credentials and enforce forced password change
                CharBuffer charBuffer = CharBuffer.wrap(newPassword);
                user.setPasswordHash(passwordEncoder.encode(charBuffer));
                // Immediately wipe cleartext buffer once BCrypt hash is produced
                Arrays.fill(newPassword, '\0');

                user.setStatus("ACTIVE");
                user.setMustChangePassword(true);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);

                // Revoke all existing sessions for this identity
                List<AuthSession> existingSessions = authSessionRepository.findByUser_UserIdAndIsActiveTrue(user.getUserId());
                for (AuthSession session : existingSessions) {
                    session.revoke("ADMIN_PROVISIONED");
                    authSessionRepository.save(session);
                }

                // Record structured audit log entry (attributable to verified operator; never logging password or hash)
                AuditLog auditLog = new AuditLog();
                auditLog.setActorName(effectiveOperator.displayName());
                auditLog.setActorEmail(effectiveOperator.identifier());
                auditLog.setAction("ADMIN_CREDENTIAL_PROVISIONED");
                auditLog.setEntityType("USER");
                auditLog.setEntityId(user.getUserId().toString());
                auditLog.setEntityName(user.getName());
                auditLog.setPreviousState("LOCKED");
                auditLog.setNewState("ACTIVE");
                auditLog.setReason("Controlled administrator credential rotation (" + effectiveOperator.source() + ").");
                auditLogRepository.save(auditLog);

                log.info("Administrator credentials successfully provisioned for user ID {} by operator {} ({})",
                    user.getUserId(), effectiveOperator.identifier(), effectiveOperator.source());
            });
        } finally {
            // Defense-in-depth: guarantee wiping sensitive in-memory password character array
            Arrays.fill(newPassword, '\0');
        }
    }

    public void provisionFromFile(Path secretFilePath, String targetEmail) throws IOException {
        provisionFromFile(secretFilePath, targetEmail, OperatorIdentity.resolveCurrent(), null);
    }

    public void provisionFromFile(Path secretFilePath, String targetEmail, OperatorIdentity operator) throws IOException {
        provisionFromFile(secretFilePath, targetEmail, operator, null);
    }

    /**
     * Reads credentials from a protected local secret file, enforces restrictive file permissions,
     * opens atomically rejecting symlinks (LinkOption.NOFOLLOW_LINKS), provisions the account,
     * and guarantees in-place zero-byte overwrite and deletion on BOTH success and failure paths.
     */
    public void provisionFromFile(Path secretFilePath, String targetEmail, OperatorIdentity operator, String operatorRecoveryKey) throws IOException {
        if (secretFilePath == null) {
            throw new IllegalArgumentException("Secret file path is required.");
        }

        // 1. Verify restrictive permissions on POSIX systems (reject group and others permissions)
        try {
            PosixFileAttributeView posixView = Files.getFileAttributeView(
                secretFilePath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS
            );
            if (posixView != null) {
                PosixFileAttributes attrs = posixView.readAttributes();
                Set<PosixFilePermission> perms = attrs.permissions();
                if (perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.GROUP_WRITE)
                    || perms.contains(PosixFilePermission.GROUP_EXECUTE) || perms.contains(PosixFilePermission.OTHERS_READ)
                    || perms.contains(PosixFilePermission.OTHERS_WRITE) || perms.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                    throw new SecurityException("Secret file has insecure permissions. File must be accessible only by owner (e.g. 0600 or 0400).");
                }
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g., Windows NTFS)
        }

        FileChannel channel = null;
        byte[] rawBytes = null;
        char[] passwordChars = null;
        long fileSize = 0;

        try {
            // 2. Open atomically with read/write rejecting symlinks to prevent path replacement races
            channel = FileChannel.open(
                secretFilePath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
            );

            fileSize = channel.size();
            if (fileSize <= 0) {
                throw new IllegalArgumentException("Secret file is empty.");
            }
            if (fileSize > 4096) {
                throw new IllegalArgumentException("Secret file exceeds maximum permitted size (4KB).");
            }

            int intSize = (int) fileSize;
            rawBytes = new byte[intSize];
            ByteBuffer buffer = ByteBuffer.wrap(rawBytes);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) {
                    break;
                }
            }

            // Decode UTF-8 bytes directly into char array without creating an immutable String
            ByteBuffer readBuffer = ByteBuffer.wrap(rawBytes);
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            CharBuffer charBuffer = decoder.decode(readBuffer);

            // Strip leading/trailing whitespace
            int len = charBuffer.length();
            while (len > 0 && Character.isWhitespace(charBuffer.charAt(len - 1))) {
                len--;
            }
            int start = 0;
            while (start < len && Character.isWhitespace(charBuffer.charAt(start))) {
                start++;
            }
            int trimmedLen = len - start;
            passwordChars = new char[trimmedLen];
            charBuffer.position(start);
            charBuffer.get(passwordChars, 0, trimmedLen);

            // Execute provisioning
            provisionAdminCredential(targetEmail, passwordChars, operator, operatorRecoveryKey);
        } finally {
            // Guarantee in-place zero-byte overwrite and deletion on BOTH success and failure
            zeroAndUnlinkFile(channel, secretFilePath, fileSize);

            // Wipe in-memory buffers
            if (rawBytes != null) {
                Arrays.fill(rawBytes, (byte) 0);
            }
            if (passwordChars != null) {
                Arrays.fill(passwordChars, '\0');
            }
        }
    }

    /**
     * Overwrites file contents in-place with zero bytes handling partial writes in a loop,
     * flushes to persistent storage, closes the channel, and deletes the file.
     */
    private void zeroAndUnlinkFile(FileChannel channel, Path secretFilePath, long fileSize) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.position(0);
                long targetSize = Math.max(fileSize, channel.size());
                byte[] zeroChunk = new byte[4096];
                ByteBuffer zeroBuffer = ByteBuffer.wrap(zeroChunk);
                long remaining = targetSize;
                while (remaining > 0) {
                    zeroBuffer.clear();
                    if (remaining < zeroChunk.length) {
                        zeroBuffer.limit((int) remaining);
                    }
                    while (zeroBuffer.hasRemaining()) {
                        channel.write(zeroBuffer);
                    }
                    remaining -= zeroChunk.length;
                }
                channel.force(true);
            } catch (Exception ex) {
                log.error("Failed to overwrite secret file {} with zeros: {}", secretFilePath, ex.getMessage());
            } finally {
                try {
                    channel.close();
                } catch (IOException ex) {
                    log.warn("Failed to close secret file channel {}: {}", secretFilePath, ex.getMessage());
                }
            }
        }

        if (secretFilePath != null) {
            try {
                Files.deleteIfExists(secretFilePath);
            } catch (IOException ex) {
                log.error("Failed to delete secret file {}: {}", secretFilePath, ex.getMessage());
            }
        }
    }
}
