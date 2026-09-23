package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attendance_corrections")
public class AttendanceCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_id")
    private Long correctionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "corrected_by", nullable = false)
    private User correctedBy;

    @Column(name = "previous_status", nullable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt = Instant.now();

    public AttendanceCorrection() {}

    public AttendanceCorrection(AttendanceRecord record, User correctedBy, String previousStatus, String newStatus, String reason) {
        this.attendanceRecord = record;
        this.correctedBy = correctedBy;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.correctedAt = Instant.now();
    }

    public Long getCorrectionId() { return correctionId; }
    public AttendanceRecord getAttendanceRecord() { return attendanceRecord; }
    public User getCorrectedBy() { return correctedBy; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public Instant getCorrectedAt() { return correctedAt; }
}
