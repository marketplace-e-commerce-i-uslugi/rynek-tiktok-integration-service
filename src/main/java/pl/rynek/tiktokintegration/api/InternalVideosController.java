package pl.rynek.tiktokintegration.api;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.tiktokintegration.api.Dtos.OfferVideoRequest;
import pl.rynek.tiktokintegration.api.Dtos.OfferVideoView;
import pl.rynek.tiktokintegration.video.VideoService;

/** Komunikacja między serwisami (nieudostępniona przez gateway). */
@RestController
public class InternalVideosController {

    private static final int MAX_BATCH = 100;

    private final VideoService videos;

    public InternalVideosController(VideoService videos) {
        this.videos = videos;
    }

    /**
     * Wsadowo: filmy i okładki dla kart ofert (catalog). Dostęp: int. {@code offerIds} rozdzielone przecinkami; niepoprawne pomijane.
     */
    @GetMapping("/internal/videos")
    public List<OfferVideoView> getVideosForOffers(@RequestParam(required = false) String offerIds) {
        if (offerIds == null || offerIds.isBlank()) {
            return List.of();
        }
        List<UUID> ids = Arrays.stream(offerIds.split(",")).map(String::trim).map(InternalVideosController::uuidOrNull)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Najwyżej " + MAX_BATCH + " ofert w jednym zapytaniu.");
        }
        return videos.forOffers(ids);
    }

    /**
     * Film oferty zapisany w catalog (albo odpięty, gdy {@code videoId} puste): film musi należeć do kanału oferty. Dostęp: int.
     */
    @PutMapping("/internal/offers/{offerId}/video")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOfferVideo(@PathVariable String offerId, @Valid @RequestBody OfferVideoRequest request) {
        UUID offer = uuidOrNull(offerId);
        if (offer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono oferty.");
        }
        videos.setOfferVideo(offer, request.channelId(), request.videoId());
    }

    private static UUID uuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
