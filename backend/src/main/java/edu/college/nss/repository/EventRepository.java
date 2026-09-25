package edu.college.nss.repository;

import edu.college.nss.domain.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.eventId = :eventId")
    Optional<Event> findByIdWithLock(@Param("eventId") UUID eventId);

    long countByStatus(String status);
    long countByUnit_UnitId(UUID unitId);

    @Query(
        value = """
            select distinct e from Event e
            left join e.participatingUnits pu
            where (:unitId is null or e.unit.unitId = :unitId or (e.status != 'DRAFT' and (pu.unitId = :unitId or e.eventScope = 'COLLEGE_WIDE')))
              and (:status is null or e.status = :status)
            order by e.startAt asc
            """,
        countQuery = """
            select count(distinct e) from Event e
            left join e.participatingUnits pu
            where (:unitId is null or e.unit.unitId = :unitId or (e.status != 'DRAFT' and (pu.unitId = :unitId or e.eventScope = 'COLLEGE_WIDE')))
              and (:status is null or e.status = :status)
            """
    )
    Page<Event> search(@Param("unitId") UUID unitId, @Param("status") String status, Pageable pageable);

    @Query(
        value = """
            select distinct e from Event e
            left join e.participatingUnits pu
            where (:unitId is null or e.unit.unitId = :unitId or pu.unitId = :unitId or e.eventScope = 'COLLEGE_WIDE')
              and e.status in ('PUBLISHED', 'OPEN', 'CLOSED', 'COMPLETED')
              and (:status is null or e.status = :status)
            order by e.startAt asc
            """,
        countQuery = """
            select count(distinct e) from Event e
            left join e.participatingUnits pu
            where (:unitId is null or e.unit.unitId = :unitId or pu.unitId = :unitId or e.eventScope = 'COLLEGE_WIDE')
              and e.status in ('PUBLISHED', 'OPEN', 'CLOSED', 'COMPLETED')
              and (:status is null or e.status = :status)
            """
    )
    Page<Event> searchPublic(@Param("unitId") UUID unitId, @Param("status") String status, Pageable pageable);
}
