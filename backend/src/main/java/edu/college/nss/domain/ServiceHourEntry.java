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

    @Column(nullable = false, length = 50)
    private String category = "REGULAR_ACTIVITY";

    @Column(name = "evidence_note", length = 1000)
    private String evidenceNote;

    @Column(name = "activity_date")
    private java.time.LocalDate activityDate;

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
        this.category = "REGULAR_ACTIVITY";
        this.createdAt = Instant.now();
    }

    public ServiceHourEntry(Volunteer volunteer, Event event, AttendanceRecord attendanceRecord,
                            BigDecimal hours, String status, User approvedBy, String description,
                            String category, String evidenceNote, java.time.LocalDate activityDate) {
        this.volunteer = volunteer;
        this.event = event;
        this.attendanceRecord = attendanceRecord;
        this.hours = hours;
        this.status = status;
        this.approvedBy = approvedBy;
        this.description = description;
        this.category = (category != null && !category.isBlank()) ? category : "REGULAR_ACTIVITY";
        this.evidenceNote = evidenceNote;
        this.activityDate = activityDate;
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
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getEvidenceNote() { return evidenceNote; }
    public void setEvidenceNote(String evidenceNote) { this.evidenceNote = evidenceNote; }
    public java.time.LocalDate getActivityDate() { return activityDate; }
    public void setActivityDate(java.time.LocalDate activityDate) { this.activityDate = activityDate; }
    public Instant getCreatedAt() { return createdAt; }
}
