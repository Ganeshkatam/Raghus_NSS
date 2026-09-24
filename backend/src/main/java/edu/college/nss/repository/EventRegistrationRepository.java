package edu.college.nss.repository;

import edu.college.nss.domain.EventRegistration;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {
    long countByEvent_EventIdAndStatus(UUID eventId, String status);
    List<EventRegistration> findByEvent_EventIdOrderByRegisteredAtAsc(UUID eventId);
    Optional<EventRegistration> findByEvent_EventIdAndVolunteer_VolunteerId(UUID eventId, UUID volunteerId);
    boolean existsByEvent_EventIdAndVolunteer_VolunteerIdAndStatus(UUID eventId, UUID volunteerId, String status);

    @Query("select count(r) from EventRegistration r where r.event.eventId = :eventId and r.status = 'REGISTERED'")
    long countRegistered(@Param("eventId") UUID eventId);
}
