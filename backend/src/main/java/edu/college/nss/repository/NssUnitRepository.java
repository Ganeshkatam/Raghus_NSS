package edu.college.nss.repository;

import edu.college.nss.domain.NssUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NssUnitRepository extends JpaRepository<NssUnit, UUID> {
    Optional<NssUnit> findByUnitNumber(String unitNumber);
    boolean existsByUnitNumber(String unitNumber);
    List<NssUnit> findByOfficer_UserId(UUID officerId);
    List<NssUnit> findByOfficer_EmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM NssUnit u WHERE u.unitId = :unitId")
    Optional<NssUnit> findByIdWithLock(@org.springframework.data.repository.query.Param("unitId") UUID unitId);
}
