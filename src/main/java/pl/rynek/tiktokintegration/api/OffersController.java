package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.tiktokintegration.api.Dtos.VideoView;
import pl.rynek.tiktokintegration.video.VideoService;

@RestController
public class OffersController {

    private final VideoService videos;

    public OffersController(VideoService videos) {
        this.videos = videos;
    }

    /**
     * Film przypisany do oferty (ścieżka pod prefiksem {@code /offers}, routowana w gateway do tego serwisu). Dostęp: pub.
     */
    @GetMapping("/api/v1/offers/{offerId}/video")
    public VideoView getOfferVideo(@PathVariable String offerId) {
        return videos.ofOffer(VideosController.uuid(offerId, "Ta oferta nie ma filmu."));
    }
}
