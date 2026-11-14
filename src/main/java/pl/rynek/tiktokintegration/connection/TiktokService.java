package pl.rynek.tiktokintegration.connection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.tiktokintegration.api.Dtos.ConnectionView;
import pl.rynek.tiktokintegration.api.Dtos.PostView;
import pl.rynek.tiktokintegration.clients.ChannelClient;
import pl.rynek.tiktokintegration.common.TokenCipher;
import pl.rynek.tiktokintegration.post.PostRepository;
import pl.rynek.tiktokintegration.post.TiktokPost;
import pl.rynek.tiktokintegration.provider.VideoProvider;
import pl.rynek.tiktokintegration.provider.VideoProvider.PostState;
import pl.rynek.tiktokintegration.provider.VideoProvider.ProviderException;
import pl.rynek.tiktokintegration.provider.VideoProvider.ProviderVideo;
import pl.rynek.tiktokintegration.provider.VideoProvider.Tokens;
import pl.rynek.tiktokintegration.video.Video;
import pl.rynek.tiktokintegration.video.VideoRepository;
import pl.rynek.tiktokintegration.video.VideoService;

/**
 * Konto TikTok sprzedawcy: zgoda OAuth (parametr {@code state}), tokeny zaszyfrowane w bazie i odświeżane przed wygaśnięciem,
 * synchronizacja filmów konta oraz publikacje z Rynku. Tylko ten serwis zna tokeny i dostawcę wideo.
 */
@Service
public class TiktokService {

    private static final Logger log = LoggerFactory.getLogger(TiktokService.class);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int PAGE = 20;
    private static final int MAX_VIDEOS = 200;
    private static final String PUBLISH_SCOPE = "video.publish";

    public enum CallbackResult { CONNECTED, DENIED, ERROR }

    private final ConnectionRepository connections;
    private final OauthStateRepository states;
    private final VideoRepository videos;
    private final PostRepository posts;
    private final VideoProvider provider;
    private final TokenCipher cipher;
    private final ChannelClient channels;
    private final VideoService videoService;
    private final Clock clock;
    private final Duration refreshMargin = Duration.ofHours(1);
    private final Duration syncEvery;

    public TiktokService(ConnectionRepository connections, OauthStateRepository states, VideoRepository videos, PostRepository posts,
                         VideoProvider provider, TokenCipher cipher, ChannelClient channels, VideoService videoService, Clock clock,
                         @Value("${rynek.tiktok.sync-every}") Duration syncEvery) {
        this.connections = connections;
        this.states = states;
        this.videos = videos;
        this.posts = posts;
        this.provider = provider;
        this.cipher = cipher;
        this.channels = channels;
        this.videoService = videoService;
        this.clock = clock;
        this.syncEvery = syncEvery;
    }

    /** Adres zgody u dostawcy. Wymaga kanału, bo filmy trafiają do kanału sprzedawcy. */
    @Transactional
    public String connectUrl(UUID userId) {
        UUID channelId = channelOf(userId);
        Instant now = clock.instant();
        states.deleteOlderThan(now.minus(STATE_TTL));
        String state = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        states.save(new OauthState(state, userId, channelId, now));
        return provider.authorizeUrl(state);
    }

    /** Powrót z TikToka: {@code state} jest jednorazowy i świeży, potem wymiana kodu na tokeny, zapis zaszyfrowany i pierwsza synchronizacja. */
    @Transactional
    public CallbackResult callback(String code, String state, String error) {
        OauthState stored = state == null ? null : states.findById(state).orElse(null);
        if (stored == null || stored.getCreatedAt().isBefore(clock.instant().minus(STATE_TTL))) {
            return CallbackResult.ERROR;
        }
        states.deleteById(state);
        if (error != null || code == null || code.isBlank()) {
            return CallbackResult.DENIED;
        }
        try {
            Tokens tokens = provider.exchange(code);
            String account = provider.account(tokens.accessToken()).displayName();
            Instant now = clock.instant();
            String access = cipher.encrypt(tokens.accessToken());
            String refresh = cipher.encrypt(tokens.refreshToken());
            TiktokConnection connection = connections.findById(stored.getUserId()).orElse(null);
            if (connection == null) {
                connection = new TiktokConnection(stored.getUserId(), stored.getChannelId(), tokens.openId(), account, tokens.scopes(), access, refresh,
                        now.plus(tokens.accessTtl()), now.plus(tokens.refreshTtl()), now);
            } else {
                connection.reconnect(stored.getChannelId(), tokens.openId(), account, tokens.scopes(), access, refresh,
                        now.plus(tokens.accessTtl()), now.plus(tokens.refreshTtl()));
            }
            connection = connections.save(connection);
            try {
                sync(connection);
            } catch (RuntimeException e) {
                log.warn("Pierwsza synchronizacja filmów konta {} nie powiodła się: {}", stored.getUserId(), e.getMessage());
            }
            return CallbackResult.CONNECTED;
        } catch (ProviderException e) {
            log.warn("TikTok odrzucił powrót OAuth: {}", e.getMessage());
            return CallbackResult.ERROR;
        }
    }

    @Transactional(readOnly = true)
    public ConnectionView connection(UUID userId) {
        return connections.findById(userId)
                .map(c -> new ConnectionView(true, c.getDisplayName(), c.scopeList(), c.getRefreshExpiresAt(), c.getLastSyncAt()))
                .orElseGet(() -> new ConnectionView(false, null, List.of(), null, null));
    }

    /** Rozłączenie: unieważnia token u dostawcy (błąd go nie blokuje) i usuwa tokeny. Filmy w ofertach zostają. */
    @Transactional
    public void disconnect(UUID userId) {
        connections.findById(userId).ifPresent(c -> {
            try {
                provider.revoke(cipher.decrypt(c.getAccessToken()));
            } catch (RuntimeException e) {
                log.warn("Nie unieważniono tokenu TikTok konta {}: {}", userId, e.getMessage());
            }
            connections.delete(c);
        });
    }

    /** Ręczna synchronizacja filmów; zwraca liczbę filmów konta. */
    @Transactional
    public int sync(UUID userId) {
        return sync(connected(userId));
    }

    /**
     * Pobiera publiczne filmy konta (do {@value #MAX_VIDEOS}), dodaje nowe i odświeża znane (tytuł, okładka, liczba wyświetleń), a filmy,
     * których konto już nie zwraca (usunięte albo prywatne), oznacza jako usunięte. Film oznaczony jako usunięty znika z odtwarzania.
     */
    @Transactional
    public int sync(TiktokConnection connection) {
        String token = freshAccessToken(connection);
        Instant now = clock.instant();
        Set<String> seen = new HashSet<>();
        String cursor = null;
        do {
            String current = cursor;
            var page = providerCall(() -> provider.listVideos(token, current, PAGE));
            for (ProviderVideo pv : page.items()) {
                seen.add(pv.externalId());
                videos.findByOwnerUserIdAndExternalId(connection.getUserId(), pv.externalId()).ifPresentOrElse(
                        v -> v.refresh(pv.title(), pv.durationSec(), pv.vertical(), pv.coverUrl(), pv.embedUrl(), pv.viewCount(), pv.captions()),
                        () -> videos.save(new Video(connection.getChannelId(), connection.getUserId(), Video.TIKTOK, pv.externalId(), pv.title(),
                                pv.durationSec(), pv.vertical(), pv.coverUrl(), pv.embedUrl(), pv.viewCount(), pv.captions(), now)));
            }
            cursor = page.nextCursor();
        } while (cursor != null && seen.size() < MAX_VIDEOS);
        if (cursor == null) {
            videos.findByOwnerUserIdAndSource(connection.getUserId(), Video.TIKTOK).stream()
                    .filter(v -> v.isActive() && !seen.contains(v.getExternalId())).forEach(Video::markRemoved);
        }
        connection.synced(now);
        return seen.size();
    }

    /** Zadanie w tle: odświeża tokeny wygasające w ciągu godziny i synchronizuje konta nieodświeżane od {@code sync-every}. */
    @Transactional
    public void runJobs() {
        Instant now = clock.instant();
        for (TiktokConnection c : connections.expiringBefore(now.plus(refreshMargin))) {
            try {
                freshAccessToken(c);
            } catch (RuntimeException e) {
                log.warn("Nie odświeżono tokenu TikTok konta {}: {}", c.getUserId(), e.getMessage());
            }
        }
        for (TiktokConnection c : connections.notSyncedSince(now.minus(syncEvery))) {
            try {
                sync(c);
            } catch (RuntimeException e) {
                log.warn("Synchronizacja filmów konta {} nie powiodła się: {}", c.getUserId(), e.getMessage());
            }
        }
    }

    // --- publikacja z Rynku na TikTok ---

    @Transactional
    public PostView createPost(UUID userId, UUID offerId, String title) {
        TiktokConnection connection = connected(userId);
        if (!connection.scopeList().contains(PUBLISH_SCOPE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Połączone konto nie ma zgody na publikowanie. Połącz konto ponownie.");
        }
        videoService.requireOfferOf(connection.getChannelId(), offerId);
        String publishId = providerCall(() -> provider.initPost(freshAccessToken(connection), title));
        posts.save(new TiktokPost(publishId, userId, offerId, title, PostState.PROCESSING.name(), clock.instant()));
        return new PostView(publishId, PostState.PROCESSING.name());
    }

    /** Status pytamy u dostawcy, dopóki publikacja trwa; po zakończeniu zostaje zapisany. */
    @Transactional
    public PostView postStatus(UUID userId, String publishId) {
        TiktokPost post = posts.findById(publishId).filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono publikacji."));
        if (PostState.PROCESSING.name().equals(post.getStatus())) {
            TiktokConnection connection = connected(userId);
            PostState state = providerCall(() -> provider.postStatus(freshAccessToken(connection), publishId));
            post.changeStatus(state.name(), clock.instant());
        }
        return new PostView(post.getPublishId(), post.getStatus());
    }

    /** Webhook dostawcy (podpis sprawdza kontroler): publikacja zakończona, nieudana albo film stał się publiczny. */
    @Transactional
    public void postEvent(String publishId, String status) {
        PostState state;
        try {
            state = PostState.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznany status publikacji.");
        }
        posts.findById(publishId).ifPresent(p -> p.changeStatus(state.name(), clock.instant()));
    }

    // --- pomocnicze ---

    private UUID channelOf(UUID userId) {
        return channels.channelOf(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Najpierw załóż kanał."));
    }

    private TiktokConnection connected(UUID userId) {
        return connections.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Połącz konto TikTok, aby wykonać tę operację."));
    }

    /** Access token ważny co najmniej godzinę: odświeżamy go refresh tokenem i zapisujemy zaszyfrowany. */
    private String freshAccessToken(TiktokConnection connection) {
        Instant now = clock.instant();
        if (connection.getAccessExpiresAt().isAfter(now.plus(refreshMargin))) {
            return cipher.decrypt(connection.getAccessToken());
        }
        Tokens tokens = providerCall(() -> provider.refresh(cipher.decrypt(connection.getRefreshToken())));
        connection.renewTokens(cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()), now.plus(tokens.accessTtl()),
                now.plus(tokens.refreshTtl()));
        return tokens.accessToken();
    }

    /** Błąd dostawcy wychodzi jako 502 z jego komunikatem, a nie 500. */
    private static <T> T providerCall(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ProviderException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }
}
