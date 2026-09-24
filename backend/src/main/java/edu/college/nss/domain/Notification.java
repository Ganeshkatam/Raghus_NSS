package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id", updatable = false, nullable = false)
    private UUID notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(length = 255)
    private String link;

    @Column(length = 50)
    private String category = "GENERAL";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(User user, String title, String message) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.isRead = false;
        this.category = "GENERAL";
        this.createdAt = Instant.now();
    }

    public Notification(User user, String title, String message, String category, String link) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.category = category != null ? category : "GENERAL";
        this.link = link;
        this.isRead = false;
        this.createdAt = Instant.now();
    }

    public UUID getNotificationId() { return notificationId; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Instant getCreatedAt() { return createdAt; }
}
