package edu.college.nss.repository;

import edu.college.nss.domain.UnitMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitMembershipRepository extends JpaRepository<UnitMembership, Long> {
    List<UnitMembership> findByUnit_UnitIdAndIsActiveTrue(Long unitId);
    List<UnitMembership> findByVolunteer_VolunteerId(Long volunteerId);
    Optional<UnitMembership> findByVolunteer_VolunteerIdAndIsActiveTrue(Long volunteerId);
    Optional<UnitMembership> findByVolunteer_VolunteerIdAndUnit_UnitIdAndIsActiveTrue(Long volunteerId, Long unitId);
}
