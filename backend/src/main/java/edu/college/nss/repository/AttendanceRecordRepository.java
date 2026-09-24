package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    Optional<AttendanceRecord> findBySession_SessionIdAndVolunteer_VolunteerId(UUID sessionId, UUID volunteerId);

    boolean existsBySession_SessionIdAndVolunteer_VolunteerId(UUID sessionId, UUID volunteerId);

    List<AttendanceRecord> findBySession_SessionId(UUID sessionId);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.session.event.eventId = :eventId")
    List<AttendanceRecord> findByEventId(@Param("eventId") UUID eventId);

    long countBySession_SessionIdAndStatus(UUID sessionId, String status);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.session.event.eventId = :eventId AND r.status = :status")
    long countByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") String status);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.volunteer.volunteerId = :volunteerId AND r.status = 'PRESENT'")
    long countAttendedByVolunteerId(@Param("volunteerId") UUID volunteerId);
}
