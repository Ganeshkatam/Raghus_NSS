package edu.college.nss.repository;

import edu.college.nss.domain.EventRegistration;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM EventRegistration r WHERE r.event.eventId = :eventId AND r.volunteer.volunteerId = :volunteerId")
    Optional<EventRegistration> findByEvent_EventIdAndVolunteer_VolunteerIdWithLock(
        @Param("eventId") UUID eventId,
        @Param("volunteerId") UUID volunteerId
    );
    List<EventRegistration> findByEvent_EventIdAndStatus(UUID eventId, String status);
    Optional<EventRegistration> findFirstByEvent_EventIdAndStatusOrderByWaitlistPositionAsc(UUID eventId, String status);
    List<EventRegistration> findByEvent_EventIdAndStatusOrderByWaitlistPositionAsc(UUID eventId, String status);
    List<EventRegistration> findByVolunteer_VolunteerIdOrderByRegisteredAtDesc(UUID volunteerId);

    @Query("select count(r) from EventRegistration r where r.event.eventId = :eventId and (r.status = 'REGISTERED' or r.status = 'CONFIRMED')")
    long countRegistered(@Param("eventId") UUID eventId);
}
