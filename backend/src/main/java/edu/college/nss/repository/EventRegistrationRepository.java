package edu.college.nss.repository;

import edu.college.nss.domain.EventRegistration;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {
    long countByEvent_EventIdAndStatus(Long eventId, String status);
    List<EventRegistration> findByEvent_EventIdOrderByRegisteredAtAsc(Long eventId);
    Optional<EventRegistration> findByEvent_EventIdAndVolunteer_VolunteerId(Long eventId, Long volunteerId);
    boolean existsByEvent_EventIdAndVolunteer_VolunteerIdAndStatus(Long eventId, Long volunteerId, String status);

    @Query("select count(r) from EventRegistration r where r.event.eventId = :eventId and r.status = 'REGISTERED'")
    long countRegistered(@Param("eventId") Long eventId);
}
