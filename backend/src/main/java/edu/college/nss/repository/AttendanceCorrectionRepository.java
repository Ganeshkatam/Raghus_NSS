package edu.college.nss.repository;

import edu.college.nss.domain.AttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {
    List<AttendanceCorrection> findByAttendanceRecord_AttendanceIdOrderByCorrectedAtDesc(Long attendanceId);
}
