package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "token_hash", length = 255)
    private String tokenHash;

    @Column(name = "qr_secret", length = 255)
    private String qrSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AttendanceSession() {}

    public AttendanceSession(Event event, User openedBy, Instant startsAt, Instant expiresAt, String qrSecret) {
        this.event = event;
        this.openedBy = openedBy;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
        this.qrSecret = qrSecret;
        this.status = "OPEN";
    }

    public boolean isOpen() {
        Instant now = Instant.now();
        return "OPEN".equals(status) && now.isBefore(expiresAt) && !now.isBefore(startsAt);
    }

    public void close() {
        if ("CLOSED".equals(this.status)) {
            throw new IllegalStateException("Attendance session is already closed.");
        }
        this.status = "CLOSED";
    }

    public void expire() {
        this.status = "EXPIRED";
    }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public User getOpenedBy() { return openedBy; }
    public void setOpenedBy(User openedBy) { this.openedBy = openedBy; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getQrSecret() { return qrSecret; }
    public void setQrSecret(String qrSecret) { this.qrSecret = qrSecret; }
    public Instant getCreatedAt() { return createdAt; }
}
