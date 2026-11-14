package pl.rynek.tiktokintegration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** Kształty zgodne z rynek-frontend/src/app/core/api/models/video.ts. */
public final class Dtos {

    private Dtos() {
    }

    /** {@code Video}: dane odtwarzania filmu kanału. {@code channel} to {@code ChannelRef} z channel-service. */
    public record VideoView(UUID id, String title, String embedUrl, String coverUrl, int durationSec, boolean vertical, long viewCount,
                            boolean captions, UUID offerId, JsonNode channel, int tone) {
    }

    /** {@code ExternalVideo}: film z połączonego konta do wyboru w kreatorze oferty. */
    public record ExternalVideoView(String externalVideoId, String title, int durationSec, String coverUrl, boolean vertical, long viewCount) {
    }

    /** {@code TiktokConnection}. */
    public record ConnectionView(boolean connected, String displayName, List<String> scopes, Instant expiresAt, Instant lastSyncAt) {
    }

    public record ConnectUrlView(String url) {
    }

    /** {@code TiktokPost}. */
    public record PostView(String publishId, String status) {
    }

    /** {@code GET /internal/videos}: film i okładka do karty oferty. */
    public record OfferVideoView(UUID offerId, UUID videoId, int durationSec, String coverUrl, String embedUrl) {
    }

    public record AttachRequest(@NotNull(message = "Podaj ofertę (offerId).") UUID offerId) {
    }

    public record ByLinkRequest(
            @NotBlank(message = "Podaj link do filmu.") @Size(max = 500, message = "Link może mieć najwyżej 500 znaków.") String link,
            UUID offerId) {
    }

    public record CreatePostRequest(
            @NotNull(message = "Podaj ofertę (offerId).") UUID offerId,
            @NotBlank(message = "Podaj tytuł filmu.") @Size(max = 150, message = "Tytuł może mieć najwyżej 150 znaków.") String title,
            UUID mediaId) {
    }

    /** Ciało {@code PUT /internal/offers/{offerId}/video}: film oferty (albo {@code null} po odpięciu) i kanał, który o tym decyduje. */
    public record OfferVideoRequest(@NotNull(message = "Podaj kanał (channelId).") UUID channelId, UUID videoId) {
    }
}
