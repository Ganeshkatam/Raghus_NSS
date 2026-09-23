package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(User user, String title, String message) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.isRead = false;
        this.createdAt = Instant.now();
    }

    public Long getNotificationId() { return notificationId; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public Instant getCreatedAt() { return createdAt; }
}
