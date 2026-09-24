package edu.college.nss.repository;

import edu.college.nss.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    long countByUser_UserIdAndIsReadFalse(UUID userId);
}
