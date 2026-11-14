package pl.rynek.tiktokintegration.connection;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Połączenie konta sprzedawcy z TikTokiem; tokeny są zaszyfrowane ({@code TokenCipher}). */
@Entity
@Table(name = "tiktok_connections")
public class TiktokConnection {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "open_id")
    private String openId;

    @Column(name = "display_name")
    private String displayName;

    private String scopes;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "refresh_expires_at")
    private Instant refreshExpiresAt;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    protected TiktokConnection() {
    }

    public TiktokConnection(UUID userId, UUID channelId, String openId, String displayName, List<String> scopes, String accessToken,
                            String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt, Instant now) {
        this.userId = userId;
        this.channelId = channelId;
        this.connectedAt = now;
        reconnect(channelId, openId, displayName, scopes, accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    /** Ponowna zgoda: nowe tokeny i konto, bez zmiany daty pierwszego połączenia i ostatniej synchronizacji. */
    public final void reconnect(UUID channelId, String openId, String displayName, List<String> scopes, String accessToken,
                                String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
        this.channelId = channelId;
        this.openId = openId;
        this.displayName = displayName;
        this.scopes = String.join(",", scopes);
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public void renewTokens(String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExpiresAt = accessExpiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public void synced(Instant now) {
        this.lastSyncAt = now;
    }

    public List<String> scopeList() {
        return scopes.isBlank() ? List.of() : Arrays.asList(scopes.split(","));
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public String getOpenId() {
        return openId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }
}
