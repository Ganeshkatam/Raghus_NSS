package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_registrations",
       uniqueConstraints = @UniqueConstraint(name = "uq_event_volunteer_registration", columnNames = {"event_id", "volunteer_id"}))
public class EventRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Long registrationId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private Volunteer volunteer;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();

    @Column(nullable = false, length = 20)
    private String status = "REGISTERED";

    public EventRegistration() {}
    public EventRegistration(Event event, Volunteer volunteer) {
        this.event = event;
        this.volunteer = volunteer;
    }

    public Long getRegistrationId() { return registrationId; }
    public Event getEvent() { return event; }
    public Volunteer getVolunteer() { return volunteer; }
    public Instant getRegisteredAt() { return registeredAt; }
    public String getStatus() { return status; }
    public void setRegistrationId(Long id) { this.registrationId = id; }
    public void setEvent(Event event) { this.event = event; }
    public void setVolunteer(Volunteer volunteer) { this.volunteer = volunteer; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public void setStatus(String status) { this.status = status; }
}
