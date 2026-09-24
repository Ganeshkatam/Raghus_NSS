package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {
    List<AttendanceSession> findByEvent_EventIdOrderByStartsAtDesc(UUID eventId);

    @Query("SELECT s FROM AttendanceSession s WHERE s.event.eventId = :eventId AND s.status = 'OPEN' AND s.expiresAt > CURRENT_TIMESTAMP ORDER BY s.startsAt DESC")
    List<AttendanceSession> findActiveSessionsForEvent(@Param("eventId") UUID eventId);

    Optional<AttendanceSession> findFirstByEvent_EventIdAndStatusOrderByStartsAtDesc(UUID eventId, String status);
}
