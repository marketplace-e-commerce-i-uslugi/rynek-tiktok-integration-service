package pl.rynek.tiktokintegration.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.tiktokintegration.api.Dtos.AttachRequest;
import pl.rynek.tiktokintegration.api.Dtos.ConnectUrlView;
import pl.rynek.tiktokintegration.api.Dtos.ConnectionView;
import pl.rynek.tiktokintegration.api.Dtos.CreatePostRequest;
import pl.rynek.tiktokintegration.api.Dtos.ExternalVideoView;
import pl.rynek.tiktokintegration.api.Dtos.PostView;
import pl.rynek.tiktokintegration.api.Dtos.VideoView;
import pl.rynek.tiktokintegration.clients.ChannelClient;
import pl.rynek.tiktokintegration.common.Cursors.Page;
import pl.rynek.tiktokintegration.common.WebhookSignature;
import pl.rynek.tiktokintegration.connection.TiktokService;
import pl.rynek.tiktokintegration.connection.TiktokService.CallbackResult;
import pl.rynek.tiktokintegration.video.VideoService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class TiktokController {

    private final TiktokService tiktok;
    private final VideoService videos;
    private final ChannelClient channels;
    private final WebhookSignature signature;
    private final JsonMapper json;
    private final String frontendUrl;

    public TiktokController(TiktokService tiktok, VideoService videos, ChannelClient channels, WebhookSignature signature, JsonMapper json,
                            @Value("${rynek.tiktok.frontend-url}") String frontendUrl) {
        this.tiktok = tiktok;
        this.videos = videos;
        this.channels = channels;
        this.signature = signature;
        this.json = json;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Zwraca adres autoryzacji TikTok z parametrem {@code state} (CSRF). Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/connect/url")
    public ConnectUrlView getConnectUrl(@AuthenticationPrincipal Jwt token) {
        return new ConnectUrlView(tiktok.connectUrl(userId(token)));
    }

    /**
     * {@code redirect_uri} zarejestrowany w TikTok: wymiana {@code code} na tokeny (zapis zaszyfrowany), potem powrót do panelu
     * sprzedawcy z wynikiem w {@code ?tiktok=connected|denied|error}. Dostęp: pub (z {@code state}).
     */
    @GetMapping("/api/v1/tiktok/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        CallbackResult result = tiktok.callback(code, state, error);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, URI.create(frontendUrl + "/sprzedawca/filmy?tiktok=" + result.name().toLowerCase()).toString()).build();
    }

    /**
     * Status połączenia (połączone, do kiedy ważne, zakres uprawnień). Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/connection")
    public ConnectionView getConnection(@AuthenticationPrincipal Jwt token) {
        return tiktok.connection(userId(token));
    }

    /**
     * Rozłączenie konta + unieważnienie tokenu. Dostęp: seller.
     */
    @DeleteMapping("/api/v1/tiktok/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt token) {
        tiktok.disconnect(userId(token));
    }

    /**
     * Ręczna synchronizacja filmów. Dostęp: seller.
     */
    @PostMapping("/api/v1/tiktok/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sync(@AuthenticationPrincipal Jwt token) {
        tiktok.sync(userId(token));
    }

    /**
     * Filmy z połączonego konta do wyboru w kreatorze oferty. Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/me/videos")
    public Page<ExternalVideoView> listMyVideos(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Integer limit) {
        return videos.accountVideos(userId(token), cursor, limit);
    }

    /**
     * Przypisanie filmu do oferty własnego kanału ({@code offerId}). Dostęp: seller.
     */
    @PostMapping("/api/v1/tiktok/me/videos/{externalVideoId}/attach")
    public VideoView attachVideoToOffer(@AuthenticationPrincipal Jwt token, @PathVariable String externalVideoId, @Valid @RequestBody AttachRequest request) {
        UUID user = userId(token);
        return videos.attach(user, channelOf(user), externalVideoId, request.offerId());
    }

    /**
     * Publikacja filmu z Rynku na TikTok: init posta, zwraca {@code publishId}. Dostęp: seller.
     */
    @PostMapping("/api/v1/tiktok/posts")
    public PostView createPost(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CreatePostRequest request) {
        return tiktok.createPost(userId(token), request.offerId(), request.title());
    }

    /**
     * Status publikacji. Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/posts/{publishId}/status")
    public PostView getPostStatus(@AuthenticationPrincipal Jwt token, @PathVariable String publishId) {
        return tiktok.postStatus(userId(token), publishId);
    }

    /**
     * Odbiornik zdarzeń TikTok (publikacja zakończona, nieudana, film publiczny). Dostęp: pub (podpis HMAC w {@code X-Signature}).
     */
    @PostMapping("/api/v1/tiktok/webhooks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestHeader(value = WebhookSignature.HEADER, required = false) String sign, @RequestBody String body) {
        if (!signature.valid(body, sign)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy podpis webhooka.");
        }
        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowa treść webhooka.");
        }
        tiktok.postEvent(event.path("publishId").asString(null), event.path("status").asString(null));
    }

    private UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private UUID channelOf(UUID userId) {
        return channels.channelOf(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Najpierw załóż kanał."));
    }
}
