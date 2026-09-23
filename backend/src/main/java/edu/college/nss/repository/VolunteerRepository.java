package edu.college.nss.repository;

import edu.college.nss.domain.Volunteer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, Long>, JpaSpecificationExecutor<Volunteer> {
    Optional<Volunteer> findByCollegeId(String collegeId);
    Optional<Volunteer> findByUser_UserId(UUID userId);
    Optional<Volunteer> findByUser_Email(String email);
    boolean existsByCollegeId(String collegeId);
    boolean existsByUser_UserId(UUID userId);
    long countByStatus(String status);

    @Query("SELECT v FROM Volunteer v WHERE " +
           "(cast(:search as string) IS NULL OR LOWER(v.user.name) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) OR LOWER(v.collegeId) LIKE LOWER(CONCAT('%', cast(:search as string), '%'))) AND " +
           "(cast(:status as string) IS NULL OR v.status = :status) AND " +
           "(cast(:department as string) IS NULL OR v.department = :department)")
    Page<Volunteer> searchVolunteers(
        @Param("search") String search,
        @Param("status") String status,
        @Param("department") String department,
        Pageable pageable
    );
}
