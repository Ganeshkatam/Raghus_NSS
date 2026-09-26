package edu.college.nss.repository;

import edu.college.nss.domain.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSession s WHERE s.refreshTokenHash = :hash")
    Optional<AuthSession> findByRefreshTokenHashWithLock(@Param("hash") String hash);

    List<AuthSession> findByUser_UserIdAndIsActiveTrue(UUID userId);

    List<AuthSession> findByTokenFamilyId(UUID tokenFamilyId);

    List<AuthSession> findByTokenFamilyIdAndIsActiveTrue(UUID tokenFamilyId);

    Optional<AuthSession> findBySessionIdAndIsActiveTrue(UUID sessionId);
}
