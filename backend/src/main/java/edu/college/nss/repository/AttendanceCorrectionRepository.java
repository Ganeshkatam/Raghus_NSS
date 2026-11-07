package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceCorrection;
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
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {
    List<AttendanceCorrection> findByAttendanceRecord_AttendanceIdOrderByCorrectedAtDesc(UUID attendanceId);
    List<AttendanceCorrection> findByStatusOrderByCorrectedAtDesc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AttendanceCorrection c WHERE c.correctionId = :id")
    Optional<AttendanceCorrection> findByIdWithLock(@Param("id") UUID id);

    boolean existsByAttendanceRecord_AttendanceIdAndStatus(UUID attendanceId, String status);
}
