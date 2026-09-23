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

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, Long>, JpaSpecificationExecutor<Volunteer> {
    Optional<Volunteer> findByCollegeId(String collegeId);
    Optional<Volunteer> findByUser_UserId(Long userId);
    Optional<Volunteer> findByUser_Email(String email);
    boolean existsByCollegeId(String collegeId);
    boolean existsByUser_UserId(Long userId);
    long countByStatus(String status);

    @Query("SELECT v FROM Volunteer v WHERE " +
           "(:search IS NULL OR LOWER(v.user.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(v.collegeId) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR v.status = :status) AND " +
           "(:department IS NULL OR v.department = :department)")
    Page<Volunteer> searchVolunteers(
        @Param("search") String search,
        @Param("status") String status,
        @Param("department") String department,
        Pageable pageable
    );
}
