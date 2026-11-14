package pl.rynek.tiktokintegration.post;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Publikacja z Rynku na TikTok (Content Posting API). Bez audytu aplikacji dostawca publikuje tylko prywatnie. */
@Entity
@Table(name = "tiktok_posts")
public class TiktokPost {

    @Id
    @Column(name = "publish_id")
    private String publishId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "offer_id")
    private UUID offerId;

    private String title;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected TiktokPost() {
    }

    public TiktokPost(String publishId, UUID userId, UUID offerId, String title, String status, Instant now) {
        this.publishId = publishId;
        this.userId = userId;
        this.offerId = offerId;
        this.title = title;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeStatus(String status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public String getPublishId() {
        return publishId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }
}
