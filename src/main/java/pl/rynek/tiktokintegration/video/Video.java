package pl.rynek.tiktokintegration.video;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Film kanału: z połączonego konta TikTok ({@code TIKTOK}) albo dodany po linku ({@code LINK}). */
@Entity
@Table(name = "videos")
public class Video {

    public static final String TIKTOK = "TIKTOK";
    public static final String LINK = "LINK";
    public static final String ACTIVE = "ACTIVE";
    public static final String REMOVED = "REMOVED";

    @Id
    private UUID id;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    private String source;

    @Column(name = "external_id")
    private String externalId;

    private String title;

    @Column(name = "duration_sec")
    private int durationSec;

    private boolean vertical;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "embed_url")
    private String embedUrl;

    @Column(name = "view_count")
    private long viewCount;

    private boolean captions;

    private String status;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Video() {
    }

    public Video(UUID channelId, UUID ownerUserId, String source, String externalId, String title, int durationSec, boolean vertical,
                 String coverUrl, String embedUrl, long viewCount, boolean captions, Instant now) {
        this.id = UUID.randomUUID();
        this.channelId = channelId;
        this.ownerUserId = ownerUserId;
        this.source = source;
        this.externalId = externalId;
        this.status = ACTIVE;
        this.createdAt = now;
        refresh(title, durationSec, vertical, coverUrl, embedUrl, viewCount, captions);
    }

    /** Dane od dostawcy (synchronizacja): tytuł, okładka (jej link wygasa), liczba wyświetleń. Film wraca do aktywnych. */
    public final void refresh(String title, int durationSec, boolean vertical, String coverUrl, String embedUrl, long viewCount, boolean captions) {
        this.title = title;
        this.durationSec = durationSec;
        this.vertical = vertical;
        this.coverUrl = coverUrl;
        this.embedUrl = embedUrl;
        this.viewCount = viewCount;
        this.captions = captions;
        this.status = ACTIVE;
    }

    public void markRemoved() {
        this.status = REMOVED;
    }

    public void linkTo(UUID offerId) {
        this.offerId = offerId;
    }

    public void changeChannel(UUID channelId) {
        this.channelId = channelId;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public boolean isVertical() {
        return vertical;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getEmbedUrl() {
        return embedUrl;
    }

    public long getViewCount() {
        return viewCount;
    }

    public boolean isCaptions() {
        return captions;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
