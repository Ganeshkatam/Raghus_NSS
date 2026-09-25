package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id", nullable = false)
    private NssUnit unit;

    @Column(name = "event_scope", nullable = false, length = 20)
    private String eventScope = "UNIT";

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "event_units",
        joinColumns = @JoinColumn(name = "event_id"),
        inverseJoinColumns = @JoinColumn(name = "unit_id")
    )
    private Set<NssUnit> participatingUnits = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "registration_open_at")
    private Instant registrationOpenAt;

    @Column(name = "registration_close_at")
    private Instant registrationCloseAt;

    @Column(nullable = false, length = 255)
    private String venue;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Event() {}

    public Event(NssUnit unit, User createdBy, String title, String description, String eventType,
                 Instant startAt, Instant endAt, Instant registrationOpenAt, Instant registrationCloseAt,
                 String venue, Integer capacity) {
        this.unit = unit;
        this.createdBy = createdBy;
        this.title = title;
        this.description = description;
        this.eventType = eventType;
        this.startAt = startAt;
        this.endAt = endAt;
        this.registrationOpenAt = registrationOpenAt;
        this.registrationCloseAt = registrationCloseAt;
        this.venue = venue;
        this.capacity = capacity;
        this.eventScope = "UNIT";
        if (unit != null) {
            this.participatingUnits.add(unit);
        }
    }

    public Event(NssUnit unit, User createdBy, String title, String description, String eventType,
                 Instant startAt, Instant endAt, Instant registrationOpenAt, Instant registrationCloseAt,
                 String venue, Integer capacity, String eventScope, Set<NssUnit> participatingUnits) {
        this(unit, createdBy, title, description, eventType, startAt, endAt, registrationOpenAt, registrationCloseAt, venue, capacity);
        if (eventScope != null && !eventScope.isBlank()) {
            this.eventScope = eventScope.trim().toUpperCase();
        }
        if (participatingUnits != null && !participatingUnits.isEmpty()) {
            this.participatingUnits = new HashSet<>(participatingUnits);
        }
    }

    public void publish() {
        requireStatus("DRAFT");
        status = "PUBLISHED";
    }

    public void open() {
        requireStatus("PUBLISHED");
        status = "OPEN";
    }

    public void close() {
        requireStatus("OPEN");
        status = "CLOSED";
    }

    public void cancel() {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw new IllegalStateException("Completed or cancelled events cannot be cancelled.");
        }
        status = "CANCELLED";
    }

    public void complete() {
        requireStatus("CLOSED");
        status = "COMPLETED";
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new IllegalStateException("Event must be " + expected + " before this transition.");
        }
    }

    @PreUpdate
    public void onUpdate() { updatedAt = Instant.now(); }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public NssUnit getUnit() { return unit; }
    public void setUnit(NssUnit unit) { this.unit = unit; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public Instant getRegistrationOpenAt() { return registrationOpenAt; }
    public void setRegistrationOpenAt(Instant registrationOpenAt) { this.registrationOpenAt = registrationOpenAt; }
    public Instant getRegistrationCloseAt() { return registrationCloseAt; }
    public void setRegistrationCloseAt(Instant registrationCloseAt) { this.registrationCloseAt = registrationCloseAt; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getEventScope() { return eventScope; }
    public void setEventScope(String eventScope) {
        if (eventScope != null && !eventScope.isBlank()) {
            this.eventScope = eventScope.trim().toUpperCase();
        }
    }

    public Set<NssUnit> getParticipatingUnits() { return participatingUnits; }
    public void setParticipatingUnits(Set<NssUnit> participatingUnits) {
        this.participatingUnits = participatingUnits != null ? new HashSet<>(participatingUnits) : new HashSet<>();
    }

    public boolean isUnitEligible(UUID targetUnitId) {
        if (targetUnitId == null) return false;
        if ("COLLEGE_WIDE".equalsIgnoreCase(this.eventScope)) {
            return true;
        }
        if (this.unit != null && targetUnitId.equals(this.unit.getUnitId())) {
            return true;
        }
        return this.participatingUnits != null && this.participatingUnits.stream()
            .anyMatch(u -> targetUnitId.equals(u.getUnitId()));
    }
}
