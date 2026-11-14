package pl.rynek.tiktokintegration.api;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.tiktokintegration.api.Dtos.ByLinkRequest;
import pl.rynek.tiktokintegration.api.Dtos.VideoView;
import pl.rynek.tiktokintegration.clients.ChannelClient;
import pl.rynek.tiktokintegration.common.Cursors.Page;
import pl.rynek.tiktokintegration.video.VideoService;

/** Filmy: dodawanie po linku (seller) i publiczny odczyt (karta oferty, Shorts, zakładka „Filmy” kanału). */
@RestController
public class VideosController {

    private final VideoService videos;
    private final ChannelClient channels;

    public VideosController(VideoService videos, ChannelClient channels) {
        this.videos = videos;
        this.channels = channels;
    }

    /**
     * Dodanie filmu po linku (oEmbed), bez połączenia konta; z {@code offerId} od razu przypisuje go do oferty. Dostęp: seller.
     */
    @PostMapping("/api/v1/videos/by-link")
    public VideoView addVideoByLink(@AuthenticationPrincipal Jwt token, @Valid @RequestBody ByLinkRequest request) {
        UUID user = UUID.fromString(token.getSubject());
        UUID channel = channels.channelOf(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Najpierw załóż kanał."));
        return videos.addByLink(user, channel, request.link(), request.offerId());
    }

    /**
     * Dane odtwarzania: {@code embedUrl}, {@code coverUrl}, czas trwania. Dostęp: pub.
     */
    @GetMapping("/api/v1/videos/{videoId}")
    public VideoView getVideo(@PathVariable String videoId) {
        return videos.get(uuid(videoId, "Nie znaleziono filmu."));
    }

    /**
     * Pionowe filmy do strumienia Shorts (źródło dla feedu). Dostęp: pub.
     */
    @GetMapping("/api/v1/videos/shorts")
    public Page<VideoView> listShorts(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return videos.shorts(cursor, limit);
    }

    static UUID uuid(String value, String notFound) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound);
        }
    }
}
