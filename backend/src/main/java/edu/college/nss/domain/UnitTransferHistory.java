package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unit_transfer_history")
public class UnitTransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transfer_id", updatable = false, nullable = false)
    private UUID transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private Volunteer volunteer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_unit_id")
    private NssUnit fromUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_unit_id", nullable = false)
    private NssUnit toUnit;

    @Column(name = "reason", length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_by_id")
    private User authorizedBy;

    @Column(name = "transferred_at", nullable = false, updatable = false)
    private Instant transferredAt = Instant.now();

    public UnitTransferHistory() {}

    public UnitTransferHistory(Volunteer volunteer, NssUnit fromUnit, NssUnit toUnit, String reason, User authorizedBy) {
        this.volunteer = volunteer;
        this.fromUnit = fromUnit;
        this.toUnit = toUnit;
        this.reason = reason;
        this.authorizedBy = authorizedBy;
        this.transferredAt = Instant.now();
    }

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public Volunteer getVolunteer() {
        return volunteer;
    }

    public void setVolunteer(Volunteer volunteer) {
        this.volunteer = volunteer;
    }

    public NssUnit getFromUnit() {
        return fromUnit;
    }

    public void setFromUnit(NssUnit fromUnit) {
        this.fromUnit = fromUnit;
    }

    public NssUnit getToUnit() {
        return toUnit;
    }

    public void setToUnit(NssUnit toUnit) {
        this.toUnit = toUnit;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public User getAuthorizedBy() {
        return authorizedBy;
    }

    public void setAuthorizedBy(User authorizedBy) {
        this.authorizedBy = authorizedBy;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(Instant transferredAt) {
        this.transferredAt = transferredAt;
    }
}
