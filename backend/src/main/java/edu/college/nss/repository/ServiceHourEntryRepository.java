package edu.college.nss.repository;

import edu.college.nss.domain.ServiceHourEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceHourEntryRepository extends JpaRepository<ServiceHourEntry, UUID> {
    Optional<ServiceHourEntry> findByAttendanceRecord_AttendanceId(UUID attendanceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServiceHourEntry s WHERE s.attendanceRecord.attendanceId = :attendanceId")
    Optional<ServiceHourEntry> findByAttendanceRecord_AttendanceIdWithLock(@Param("attendanceId") UUID attendanceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServiceHourEntry s WHERE s.entryId = :id")
    Optional<ServiceHourEntry> findByIdWithLock(@Param("id") UUID id);

    boolean existsByVolunteer_VolunteerIdAndEvent_EventIdAndStatusIn(UUID volunteerId, UUID eventId, Collection<String> statuses);

    boolean existsByAttendanceRecord_AttendanceId(UUID attendanceId);

    @Query("SELECT COALESCE(SUM(s.hours), 0) FROM ServiceHourEntry s WHERE s.volunteer.volunteerId = :volunteerId AND s.status = 'APPROVED'")
    BigDecimal sumApprovedHoursForVolunteer(@Param("volunteerId") UUID volunteerId);

    @Query("SELECT COALESCE(SUM(s.hours), 0) FROM ServiceHourEntry s WHERE s.status = 'APPROVED'")
    BigDecimal sumAllApprovedHours();

    @Query("SELECT COALESCE(SUM(s.hours), 0) FROM ServiceHourEntry s WHERE s.status = 'APPROVED' AND s.event.unit.unitId = :unitId")
    BigDecimal sumApprovedHoursForUnit(@Param("unitId") UUID unitId);

    @Query("SELECT COALESCE(SUM(s.hours), 0) FROM ServiceHourEntry s WHERE s.volunteer.volunteerId = :volunteerId AND s.status = 'APPROVED' AND s.category = :category")
    BigDecimal sumApprovedHoursForVolunteerAndCategory(@Param("volunteerId") UUID volunteerId, @Param("category") String category);

    List<ServiceHourEntry> findByVolunteer_VolunteerIdOrderByCreatedAtDesc(UUID volunteerId);

    List<ServiceHourEntry> findByStatusOrderByCreatedAtDesc(String status);
}
