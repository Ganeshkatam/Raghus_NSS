package edu.college.nss.repository;

import edu.college.nss.domain.NssUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NssUnitRepository extends JpaRepository<NssUnit, Long> {
    Optional<NssUnit> findByUnitNumber(String unitNumber);
    boolean existsByUnitNumber(String unitNumber);
    List<NssUnit> findByOfficer_UserId(Long officerId);
}
