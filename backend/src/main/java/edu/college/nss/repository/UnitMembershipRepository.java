package edu.college.nss.repository;

import edu.college.nss.domain.UnitMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitMembershipRepository extends JpaRepository<UnitMembership, UUID> {
    List<UnitMembership> findByUnit_UnitIdAndIsActiveTrue(UUID unitId);
    List<UnitMembership> findByVolunteer_VolunteerId(UUID volunteerId);
    Optional<UnitMembership> findByVolunteer_VolunteerIdAndIsActiveTrue(UUID volunteerId);
    Optional<UnitMembership> findByVolunteer_VolunteerIdAndUnit_UnitIdAndIsActiveTrue(UUID volunteerId, UUID unitId);
}
