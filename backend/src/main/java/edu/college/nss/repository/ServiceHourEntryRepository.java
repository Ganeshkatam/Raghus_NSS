package edu.college.nss.repository;

import edu.college.nss.domain.ServiceHourEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceHourEntryRepository extends JpaRepository<ServiceHourEntry, Long> {
    Optional<ServiceHourEntry> findByAttendanceRecord_AttendanceId(Long attendanceId);

    @Query("SELECT COALESCE(SUM(s.hours), 0) FROM ServiceHourEntry s WHERE s.volunteer.volunteerId = :volunteerId AND s.status = 'APPROVED'")
    BigDecimal sumApprovedHoursForVolunteer(@Param("volunteerId") Long volunteerId);

    List<ServiceHourEntry> findByVolunteer_VolunteerIdOrderByCreatedAtDesc(Long volunteerId);

    List<ServiceHourEntry> findByStatusOrderByCreatedAtDesc(String status);
}
