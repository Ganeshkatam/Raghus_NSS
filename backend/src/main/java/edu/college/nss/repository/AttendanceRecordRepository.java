package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    Optional<AttendanceRecord> findBySession_SessionIdAndVolunteer_VolunteerId(UUID sessionId, UUID volunteerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AttendanceRecord r WHERE r.session.sessionId = :sessionId AND r.volunteer.volunteerId = :volunteerId")
    Optional<AttendanceRecord> findBySession_SessionIdAndVolunteer_VolunteerIdWithLock(
        @Param("sessionId") UUID sessionId,
        @Param("volunteerId") UUID volunteerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AttendanceRecord r WHERE r.attendanceId = :id")
    Optional<AttendanceRecord> findByIdWithLock(@Param("id") UUID id);

    boolean existsBySession_SessionIdAndVolunteer_VolunteerId(UUID sessionId, UUID volunteerId);

    List<AttendanceRecord> findBySession_SessionId(UUID sessionId);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.session.event.eventId = :eventId")
    List<AttendanceRecord> findByEventId(@Param("eventId") UUID eventId);

    long countBySession_SessionIdAndStatus(UUID sessionId, String status);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.session.event.eventId = :eventId AND r.status = :status")
    long countByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") String status);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.volunteer.volunteerId = :volunteerId AND r.status = 'PRESENT'")
    long countAttendedByVolunteerId(@Param("volunteerId") UUID volunteerId);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.session.event.eventId = :eventId AND r.volunteer.volunteerId = :volunteerId AND r.status = 'PRESENT'")
    List<AttendanceRecord> findPresentRecordsByEventAndVolunteer(@Param("eventId") UUID eventId, @Param("volunteerId") UUID volunteerId);
}
