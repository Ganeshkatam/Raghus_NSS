package edu.college.nss.repository;

import edu.college.nss.domain.VolunteerStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VolunteerStatusHistoryRepository extends JpaRepository<VolunteerStatusHistory, UUID> {
    List<VolunteerStatusHistory> findByVolunteer_VolunteerIdOrderByCreatedAtDesc(UUID volunteerId);
}
