package edu.college.nss.repository;

import edu.college.nss.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSession> findByUser_UserIdAndIsActiveTrue(UUID userId);

    List<AuthSession> findByTokenFamilyId(UUID tokenFamilyId);

    Optional<AuthSession> findBySessionIdAndIsActiveTrue(UUID sessionId);
}
