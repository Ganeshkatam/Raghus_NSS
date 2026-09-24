package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_registrations",
       uniqueConstraints = @UniqueConstraint(name = "uq_event_volunteer_registration", columnNames = {"event_id", "volunteer_id"}))
public class EventRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "registration_id", updatable = false, nullable = false)
    private UUID registrationId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private Volunteer volunteer;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();

    @Column(nullable = false, length = 20)
    private String status = "CONFIRMED";

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    public EventRegistration() {}
    public EventRegistration(Event event, Volunteer volunteer) {
        this.event = event;
        this.volunteer = volunteer;
        this.status = "CONFIRMED";
    }

    public EventRegistration(Event event, Volunteer volunteer, String status, Integer waitlistPosition) {
        this.event = event;
        this.volunteer = volunteer;
        this.status = status;
        this.waitlistPosition = waitlistPosition;
    }

    public UUID getRegistrationId() { return registrationId; }
    public Event getEvent() { return event; }
    public Volunteer getVolunteer() { return volunteer; }
    public Instant getRegisteredAt() { return registeredAt; }
    public String getStatus() { return status; }
    public Integer getWaitlistPosition() { return waitlistPosition; }
    public String getCancellationReason() { return cancellationReason; }

    public void setRegistrationId(UUID id) { this.registrationId = id; }
    public void setEvent(Event event) { this.event = event; }
    public void setVolunteer(Volunteer volunteer) { this.volunteer = volunteer; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public void setStatus(String status) { this.status = status; }
    public void setWaitlistPosition(Integer waitlistPosition) { this.waitlistPosition = waitlistPosition; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
}
