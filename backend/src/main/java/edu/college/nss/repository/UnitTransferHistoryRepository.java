package edu.college.nss.repository;

import edu.college.nss.domain.UnitTransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitTransferHistoryRepository extends JpaRepository<UnitTransferHistory, UUID> {
    List<UnitTransferHistory> findByVolunteer_VolunteerIdOrderByTransferredAtDesc(UUID volunteerId);
}
