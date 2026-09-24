package edu.college.nss.service;

import edu.college.nss.domain.Notification;
import edu.college.nss.domain.User;
import edu.college.nss.repository.NotificationRepository;
import edu.college.nss.repository.UserRepository;
import edu.college.nss.web.dto.AnnouncementDTOs.NotificationResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void sendNotification(User recipient, String title, String message, String category, String link) {
        if (recipient == null) return;
        Notification notification = new Notification(recipient, title, message, category, link);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getUsername()));

        return notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId())
            .stream()
            .map(n -> new NotificationResponse(
                n.getNotificationId(),
                n.getTitle(),
                n.getMessage(),
                n.getCategory(),
                n.getLink(),
                n.getIsRead(),
                n.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getUsername()));

        return notificationRepository.countByUser_UserIdAndIsReadFalse(user.getUserId());
    }

    @Transactional
    public void markAsRead(UUID notificationId, UserDetails principal) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!notification.getUser().getEmail().equalsIgnoreCase(principal.getUsername())) {
            throw new AccessDeniedException("You do not have permission to modify this notification.");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getUsername()));

        List<Notification> unread = notificationRepository.findByUser_UserIdAndIsReadFalse(user.getUserId());
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }
}
