package edu.college.nss.repository;

import edu.college.nss.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    long countByUser_UserIdAndIsReadFalse(Long userId);
}
