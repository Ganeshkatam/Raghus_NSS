package edu.college.nss.repository;

import edu.college.nss.domain.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.eventId = :eventId")
    Optional<Event> findByIdWithLock(@Param("eventId") Long eventId);

    @Query("""
        select e from Event e
        where (:unitId is null or e.unit.unitId = :unitId)
          and (:status is null or e.status = :status)
        order by e.startAt asc
        """)
    Page<Event> search(@Param("unitId") Long unitId, @Param("status") String status, Pageable pageable);

    @Query("""
        select e from Event e
        where (:unitId is null or e.unit.unitId = :unitId)
          and e.status in ('PUBLISHED', 'OPEN', 'CLOSED', 'COMPLETED')
        order by e.startAt asc
        """)
    Page<Event> searchPublic(@Param("unitId") Long unitId, Pageable pageable);
}
