package pl.rynek.tiktokintegration.video;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.tiktokintegration.api.Dtos.ExternalVideoView;
import pl.rynek.tiktokintegration.api.Dtos.OfferVideoView;
import pl.rynek.tiktokintegration.api.Dtos.VideoView;
import pl.rynek.tiktokintegration.clients.CatalogClient;
import pl.rynek.tiktokintegration.clients.ChannelClient;
import pl.rynek.tiktokintegration.common.Cursors;
import pl.rynek.tiktokintegration.common.Cursors.Page;
import pl.rynek.tiktokintegration.provider.VideoProvider;
import pl.rynek.tiktokintegration.provider.VideoProvider.Oembed;
import tools.jackson.databind.JsonNode;

/** Filmy kanałów: odczyt (karta oferty, kanał, Shorts), dodawanie po linku i przypisywanie do ofert. */
@Service
public class VideoService {

    private final VideoRepository videos;
    private final VideoProvider provider;
    private final ChannelClient channels;
    private final CatalogClient catalog;
    private final Clock clock;

    public VideoService(VideoRepository videos, VideoProvider provider, ChannelClient channels, CatalogClient catalog, Clock clock) {
        this.videos = videos;
        this.provider = provider;
        this.channels = channels;
        this.catalog = catalog;
        this.clock = clock;
    }

    // --- odczyt (publiczny) ---

    @Transactional(readOnly = true)
    public VideoView get(UUID videoId) {
        Video video = videos.findById(videoId).filter(Video::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono filmu."));
        return views(List.of(video)).getFirst();
    }

    @Transactional(readOnly = true)
    public VideoView ofOffer(UUID offerId) {
        Video video = videos.findFirstByOfferIdAndStatus(offerId, Video.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ta oferta nie ma filmu."));
        return views(List.of(video)).getFirst();
    }

    @Transactional(readOnly = true)
    public Page<VideoView> ofChannel(UUID channelId, String cursor, Integer limit) {
        return Cursors.page(cursor, limit, (page, n) -> videos.ofChannel(channelId, page), this::views);
    }

    @Transactional(readOnly = true)
    public Page<VideoView> shorts(String cursor, Integer limit) {
        return Cursors.page(cursor, limit, (page, n) -> videos.shorts(page), this::views);
    }

    /** Film i okładka dla kart ofert (catalog); oferty bez filmu nie występują w wyniku. */
    @Transactional(readOnly = true)
    public List<OfferVideoView> forOffers(Collection<UUID> offerIds) {
        if (offerIds.isEmpty()) {
            return List.of();
        }
        return videos.findByOfferIdInAndStatus(offerIds, Video.ACTIVE).stream()
                .map(v -> new OfferVideoView(v.getOfferId(), v.getId(), v.getDurationSec(), v.getCoverUrl(), v.getEmbedUrl())).toList();
    }

    // --- sprzedawca ---

    @Transactional(readOnly = true)
    public Page<ExternalVideoView> accountVideos(UUID userId, String cursor, Integer limit) {
        return Cursors.page(cursor, limit, (page, n) -> videos.accountVideos(userId, page),
                list -> list.stream().map(v -> new ExternalVideoView(v.getExternalId(), v.getTitle(), v.getDurationSec(), v.getCoverUrl(),
                        v.isVertical(), v.getViewCount())).toList());
    }

    /** Film z połączonego konta przypisany do oferty własnego kanału (także szkicu). */
    @Transactional
    public VideoView attach(UUID userId, UUID channelId, String externalVideoId, UUID offerId) {
        Video video = videos.findByOwnerUserIdAndExternalId(userId, externalVideoId)
                .filter(v -> v.isActive() && Video.TIKTOK.equals(v.getSource()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono filmu na Twoim koncie TikTok. Zsynchronizuj filmy."));
        requireOfferOf(channelId, offerId);
        link(video, offerId);
        return views(List.of(video)).getFirst();
    }

    /** Film po linku (oEmbed), bez połączenia konta; ten sam link dodany ponownie zwraca ten sam film. */
    @Transactional
    public VideoView addByLink(UUID userId, UUID channelId, String link, UUID offerId) {
        Oembed oembed;
        try {
            oembed = provider.oembed(link);
        } catch (VideoProvider.ProviderException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (offerId != null) {
            requireOfferOf(channelId, offerId);
        }
        Video video = videos.findByOwnerUserIdAndExternalId(userId, oembed.externalId()).orElseGet(() -> videos.save(
                new Video(channelId, userId, Video.LINK, oembed.externalId(), oembed.title(), 0, true, oembed.coverUrl(), oembed.embedUrl(), 0, false, clock.instant())));
        if (Video.LINK.equals(video.getSource())) {
            // Dane z oEmbed odświeżamy przy każdym dodaniu (adres osadzenia zależy od dostawcy, a usunięty film wraca do aktywnych).
            video.refresh(oembed.title(), 0, true, oembed.coverUrl(), oembed.embedUrl(), 0, false);
        }
        if (offerId != null) {
            link(video, offerId);
        }
        return views(List.of(video)).getFirst();
    }

    // --- wewnętrzne (catalog) ---

    /** Catalog zapisał film oferty (albo go odpiął): film musi należeć do kanału oferty; oferta ma najwyżej jeden film. */
    @Transactional
    public void setOfferVideo(UUID offerId, UUID channelId, UUID videoId) {
        if (videoId == null) {
            videos.unlinkOffer(offerId);
            return;
        }
        Video video = videos.findById(videoId).filter(Video::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono filmu."));
        if (!video.getChannelId().equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Możesz dodać tylko film swojego kanału.");
        }
        link(video, offerId);
    }

    // --- pomocnicze ---

    /** Oferta musi należeć do kanału sprzedawcy; inaczej 404 (brak) albo 403 (cudza). */
    public void requireOfferOf(UUID channelId, UUID offerId) {
        UUID owner = catalog.channelOfOffer(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono oferty."));
        if (!owner.equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "To nie jest oferta Twojego kanału.");
        }
    }

    /**
     * Film ma jedną ofertę. Film przypisany do innej, istniejącej oferty nie przeskakuje po cichu (oferta, którą zostawia, nadal miałaby
     * w catalog jego {@code videoId}): najpierw trzeba go z niej zdjąć. Oferta, której już nie ma, nie blokuje filmu.
     */
    private void link(Video video, UUID offerId) {
        UUID current = video.getOfferId();
        if (current != null && !current.equals(offerId) && catalog.channelOfOffer(current).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten film jest już przypisany do innej oferty. Najpierw usuń go z tamtej oferty.");
        }
        videos.unlinkOthers(offerId, video.getId());
        video.linkTo(offerId);
    }

    /** Widoki z danymi kanałów pobranymi jednym wywołaniem; kanał, którego nie ma, daje {@code channel = null}. */
    List<VideoView> views(List<Video> list) {
        Map<UUID, JsonNode> byId = channels.channels(list.stream().map(Video::getChannelId).collect(Collectors.toSet()));
        return list.stream().map(v -> new VideoView(v.getId(), v.getTitle(), v.getEmbedUrl(), v.getCoverUrl(), v.getDurationSec(), v.isVertical(),
                v.getViewCount(), v.isCaptions(), v.getOfferId(), byId.get(v.getChannelId()), tone(v.getId()))).toList();
    }

    /** Kolor okładki media-1…4 z design systemu, stały dla filmu. */
    public static int tone(UUID id) {
        return Math.floorMod(id.hashCode(), 4) + 1;
    }

    public Optional<Video> byExternalId(String externalId) {
        return videos.findFirstByExternalIdAndStatus(externalId, Video.ACTIVE);
    }
}
