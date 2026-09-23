package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findBySession_SessionIdAndVolunteer_VolunteerId(Long sessionId, Long volunteerId);

    boolean existsBySession_SessionIdAndVolunteer_VolunteerId(Long sessionId, Long volunteerId);

    List<AttendanceRecord> findBySession_SessionId(Long sessionId);

    @Query("SELECT r FROM AttendanceRecord r WHERE r.session.event.eventId = :eventId")
    List<AttendanceRecord> findByEventId(@Param("eventId") Long eventId);

    long countBySession_SessionIdAndStatus(Long sessionId, String status);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.volunteer.volunteerId = :volunteerId AND r.status = 'PRESENT'")
    long countAttendedByVolunteerId(@Param("volunteerId") Long volunteerId);
}
