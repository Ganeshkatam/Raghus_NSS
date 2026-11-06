package edu.college.nss.service;

import edu.college.nss.security.CustomUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.util.Objects;

public record OperatorIdentity(
    String identifier,
    String displayName,
    String source
) {
    public OperatorIdentity {
        Objects.requireNonNull(identifier, "Operator identifier must not be null");
        Objects.requireNonNull(displayName, "Operator displayName must not be null");
        Objects.requireNonNull(source, "Operator source must not be null");
    }

    /**
     * Resolves the operator identity from the authenticated Spring Security context.
     * Throws SecurityException if no authenticated context is present.
     */
    public static OperatorIdentity fromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new SecurityException("No authenticated security context available for operator attribution.");
        }
        String identifier = auth.getName();
        String displayName = auth.getName();
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            if (userDetails.getName() != null && !userDetails.getName().isBlank()) {
                displayName = userDetails.getName();
            }
            if (userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
                identifier = userDetails.getUsername();
            }
        }
        return new OperatorIdentity(identifier, displayName, "SECURITY_CONTEXT");
    }

    /**
     * Resolves operator identity from the operating system process runtime.
     * Captures verified OS user, hostname, and JVM process ID.
     */
    public static OperatorIdentity fromSystemProcess() {
        String osUser = System.getProperty("user.name", "unknown-operator");
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            hostName = "local-host";
        }
        long pid = ProcessHandle.current().pid();
        return new OperatorIdentity(
            "os-user:" + osUser + "@" + hostName + "[pid=" + pid + "]",
            "System Process (" + osUser + ")",
            "SYSTEM_CLI"
        );
    }

    /**
     * Automatically resolves current operator: prefers authenticated SecurityContext,
     * falling back strictly to verified OS process attributes.
     */
    public static OperatorIdentity resolveCurrent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return fromSecurityContext();
        }
        return fromSystemProcess();
    }
}
