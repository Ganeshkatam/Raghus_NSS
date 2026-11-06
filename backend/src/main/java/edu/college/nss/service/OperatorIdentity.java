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
     * Resolves the operator identity from the active Spring Security context or host environment.
     * Never trusts unauthenticated or caller-asserted strings.
     */
    public static OperatorIdentity resolveCurrent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
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

        // Host / CLI maintenance process environment
        String osUser = System.getProperty("user.name", "unknown-operator");
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            hostName = "local-host";
        }
        return new OperatorIdentity("cli:" + osUser + "@" + hostName, "CLI Operator (" + osUser + ")", "SYSTEM_CLI");
    }

    /**
     * Factory for verified CLI execution where explicit administrative attribution is provided.
     */
    public static OperatorIdentity fromVerifiedCli(String operatorEmail) {
        if (operatorEmail == null || operatorEmail.isBlank()) {
            throw new IllegalArgumentException("Operator email is required for verified CLI provisioning.");
        }
        String osUser = System.getProperty("user.name", "unknown-operator");
        return new OperatorIdentity(operatorEmail.trim().toLowerCase(), "CLI Operator (" + osUser + ")", "VERIFIED_CLI");
    }
}

