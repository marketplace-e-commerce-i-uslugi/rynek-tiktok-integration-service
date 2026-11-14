package pl.rynek.tiktokintegration.connection;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Jednorazowy parametr {@code state} z adresu autoryzacji (CSRF): wiąże powrót z TikToka z kontem, które go zażądało. */
@Entity
@Table(name = "tiktok_oauth_states")
public class OauthState {

    @Id
    private String state;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected OauthState() {
    }

    public OauthState(String state, UUID userId, UUID channelId, Instant createdAt) {
        this.state = state;
        this.userId = userId;
        this.channelId = channelId;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
