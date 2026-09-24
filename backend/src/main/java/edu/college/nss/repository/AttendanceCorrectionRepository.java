package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {
    List<AttendanceCorrection> findByAttendanceRecord_AttendanceIdOrderByCorrectedAtDesc(UUID attendanceId);
}
