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
import org.springframework.transaction.annotation.Transactional;

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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class AdminCredentialProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AdminCredentialProvisioningService.class);

    public static final Set<String> APPROVED_ADMIN_EMAILS = Set.of(
        "admin@raghunss.edu",
        "katamganesh61@gmail.com"
    );

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCredentialProvisioningService(
        UserRepository userRepository,
        AuthSessionRepository authSessionRepository,
        AuditLogRepository auditLogRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
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

    public void provisionAdminCredential(String targetEmail, char[] newPassword) {
        provisionAdminCredential(targetEmail, newPassword, OperatorIdentity.resolveCurrent());
    }

    public void provisionAdminCredential(String targetEmail, char[] newPassword, String operatorEmail) {
        provisionAdminCredential(targetEmail, newPassword, OperatorIdentity.fromVerifiedCli(operatorEmail));
    }

    /**
     * Executes atomic, operator-authorized administrator credential provisioning.
     *
     * @param targetEmail The administrator account email to provision
     * @param newPassword The new temporary password character array (zeroed in finally block)
     * @param operator Trusted identity of the executing operator
     * @throws SecurityException if target email is not approved or target lacks ROLE_ADMIN
     * @throws IllegalStateException if account is missing or not locked
     * @throws IllegalArgumentException if password fails policy requirements
     */
    @Transactional
    public void provisionAdminCredential(String targetEmail, char[] newPassword, OperatorIdentity operator) {
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
            // 2. Validate password complexity using policy
            PasswordPolicy.validate(newPassword);

            // 3. Lock user row with pessimistic write lock to prevent race conditions
            User user = userRepository.findByEmailWithLock(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException(
                    "Target account does not exist. Automatic account creation is prohibited; an existing administrator identity must be present."
                ));

            // 4. Verify account is currently LOCKED
            if (!"LOCKED".equalsIgnoreCase(user.getStatus())) {
                throw new IllegalStateException(
                    "Target account is not locked (current status=" + user.getStatus() + "). Provisioning can only be performed on locked accounts."
                );
            }

            // 5. Verify account is ALREADY an administrator (prevent privilege escalation)
            boolean hasAdminRole = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r.getName()));
            if (!hasAdminRole) {
                throw new SecurityException("Target account does not possess ROLE_ADMIN. Promoting non-administrator accounts is strictly prohibited.");
            }

            // 6. Update credentials and enforce forced password change
            CharBuffer charBuffer = CharBuffer.wrap(newPassword);
            user.setPasswordHash(passwordEncoder.encode(charBuffer));
            user.setStatus("ACTIVE");
            user.setMustChangePassword(true);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            // 7. Revoke all existing sessions for this identity
            List<AuthSession> existingSessions = authSessionRepository.findByUser_UserIdAndIsActiveTrue(user.getUserId());
            for (AuthSession session : existingSessions) {
                session.revoke("ADMIN_PROVISIONED");
                authSessionRepository.save(session);
            }

            // 8. Record structured audit log entry (attributable to verified operator; never logging password or hash)
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
        } finally {
            // Securely wipe sensitive in-memory password character array
            Arrays.fill(newPassword, '\0');
        }
    }

    public void provisionFromFile(Path secretFilePath, String targetEmail) throws IOException {
        provisionFromFile(secretFilePath, targetEmail, OperatorIdentity.resolveCurrent());
    }

    public void provisionFromFile(Path secretFilePath, String targetEmail, String operatorEmail) throws IOException {
        provisionFromFile(secretFilePath, targetEmail, OperatorIdentity.fromVerifiedCli(operatorEmail));
    }

    /**
     * Reads credentials from a protected local secret file, enforces restrictive file permissions,
     * opens atomically rejecting symlinks (LinkOption.NOFOLLOW_LINKS), provisions the account,
     * overwrites file contents with zeros, and deletes the file.
     */
    public void provisionFromFile(Path secretFilePath, String targetEmail, OperatorIdentity operator) throws IOException {
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

        // 2. Open atomically rejecting symlinks to prevent path replacement races
        byte[] rawBytes = null;
        char[] passwordChars = null;
        try (FileChannel channel = FileChannel.open(
                secretFilePath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        )) {
            long size = channel.size();
            if (size <= 0) {
                throw new IllegalArgumentException("Secret file is empty.");
            }
            if (size > 4096) {
                throw new IllegalArgumentException("Secret file exceeds maximum permitted size (4KB).");
            }

            int intSize = (int) size;
            rawBytes = new byte[intSize];
            ByteBuffer buffer = ByteBuffer.wrap(rawBytes);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
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
            provisionAdminCredential(targetEmail, passwordChars, operator);

            // Overwrite file contents with zeros before unlinking
            channel.position(0);
            byte[] zeroBytes = new byte[intSize];
            ByteBuffer zeroBuffer = ByteBuffer.wrap(zeroBytes);
            channel.write(zeroBuffer);
            channel.force(true);
        } finally {
            // Wipe in-memory buffers
            if (rawBytes != null) {
                Arrays.fill(rawBytes, (byte) 0);
            }
            if (passwordChars != null) {
                Arrays.fill(passwordChars, '\0');
            }
            // Delete file from disk
            try {
                Files.deleteIfExists(secretFilePath);
            } catch (IOException ex) {
                log.warn("Failed to delete secret file after provisioning: {}", ex.getMessage());
            }
        }
    }
}
