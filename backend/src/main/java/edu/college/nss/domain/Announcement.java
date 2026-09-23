package edu.college.nss.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id")
    private Long announcementId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id")
    private NssUnit unit;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Announcement() {}

    public Announcement(User createdBy, NssUnit unit, String title, String content, Instant publishedAt, Instant expiresAt) {
        this.createdBy = createdBy;
        this.unit = unit;
        this.title = title;
        this.content = content;
        this.publishedAt = publishedAt != null ? publishedAt : Instant.now();
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Long getAnnouncementId() { return announcementId; }
    public User getCreatedBy() { return createdBy; }
    public NssUnit getUnit() { return unit; }
    public void setUnit(NssUnit unit) { this.unit = unit; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
