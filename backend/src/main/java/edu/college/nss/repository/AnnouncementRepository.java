package edu.college.nss.repository;

import edu.college.nss.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    List<Announcement> findAllByOrderByPublishedAtDesc();

    List<Announcement> findByUnitIsNullOrderByPublishedAtDesc();

    @Query("SELECT a FROM Announcement a WHERE a.unit.unitId = :unitId OR a.unit IS NULL ORDER BY a.publishedAt DESC")
    List<Announcement> findVisibleForUnit(@Param("unitId") UUID unitId);

    @Query("SELECT a FROM Announcement a WHERE a.publishedAt <= :now AND (a.expiresAt IS NULL OR a.expiresAt > :now) ORDER BY a.publishedAt DESC")
    List<Announcement> findActivePublic(@Param("now") Instant now);

    @Query("SELECT count(a) FROM Announcement a WHERE a.publishedAt <= :now AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    long countActivePublic(@Param("now") Instant now);
}
