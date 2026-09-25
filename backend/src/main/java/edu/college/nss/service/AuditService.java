package edu.college.nss.service;

import edu.college.nss.domain.AuditLog;
import edu.college.nss.domain.User;
import edu.college.nss.repository.AuditLogRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.web.dto.AuditLogItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(
        User actor,
        String action,
        String entityType,
        String entityId,
        String entityName,
        String previousState,
        String newState,
        String reason
    ) {
        UUID actorUserId = actor != null ? actor.getUserId() : null;
        String actorName = actor != null ? actor.getName() : "System";
        String actorEmail = actor != null ? actor.getEmail() : "system@college.edu";
        AuditLog log = new AuditLog(actorUserId, actorName, actorEmail, action, entityType, entityId, entityName, previousState, newState, reason);
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(
        String actorEmail,
        String action,
        String entityType,
        String entityId,
        String entityName,
        String previousState,
        String newState,
        String reason
    ) {
        User actor = actorEmail != null ? userRepository.findByEmail(actorEmail).orElse(null) : null;
        logEvent(actor, action, entityType, entityId, entityName, previousState, newState, reason);
    }

    @Transactional(readOnly = true)
    public List<AuditLogItem> getRecentAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
            .map(log -> new AuditLogItem(
                log.getAuditId(),
                log.getEntityType(),
                log.getEntityName() != null ? log.getEntityName() : log.getEntityId(),
                log.getAction(),
                log.getPreviousState() != null ? log.getPreviousState() : "NONE",
                log.getNewState() != null ? log.getNewState() : "NONE",
                log.getReason() != null ? log.getReason() : "",
                log.getActorName() != null ? log.getActorName() : "System",
                log.getTimestamp()
            ))
            .collect(Collectors.toList());
    }
}
