package edu.college.nss.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_hour_entries")
public class ServiceHourEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "entry_id", updatable = false, nullable = false)
    private UUID entryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private Volunteer volunteer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id")
    private AttendanceRecord attendanceRecord;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    @Column(nullable = false, length = 20)
    private String status = "APPROVED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ServiceHourEntry() {}

    public ServiceHourEntry(Volunteer volunteer, Event event, AttendanceRecord attendanceRecord,
                            BigDecimal hours, String status, User approvedBy, String description) {
        this.volunteer = volunteer;
        this.event = event;
        this.attendanceRecord = attendanceRecord;
        this.hours = hours;
        this.status = status;
        this.approvedBy = approvedBy;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getEntryId() { return entryId; }
    public Volunteer getVolunteer() { return volunteer; }
    public Event getEvent() { return event; }
    public AttendanceRecord getAttendanceRecord() { return attendanceRecord; }
    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal hours) { this.hours = hours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
}
