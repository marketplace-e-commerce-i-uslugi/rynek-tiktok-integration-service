package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.tiktokintegration.api.dto.AddVideoByLinkRequest;

import static pl.rynek.tiktokintegration.common.NotImplemented.notImplemented;

@RestController
public class VideosController {

    /**
     * Dodanie filmu po linku (oEmbed), bez połączenia konta. Dostęp: seller.
     */
    @PostMapping("/api/v1/videos/by-link")
    public void addVideoByLink(@RequestBody AddVideoByLinkRequest request) {
        throw notImplemented();
    }

    /**
     * Dane odtwarzania: embedUrl, coverUrl (świeża), czas trwania. Dostęp: pub.
     */
    @GetMapping("/api/v1/videos/{videoId}")
    public void getVideo(@PathVariable String videoId) {
        throw notImplemented();
    }

    /**
     * Pionowe filmy do strumienia Shorts (źródło dla feedu). Dostęp: pub.
     */
    @GetMapping("/api/v1/videos/shorts")
    public void listShorts(@RequestParam(required = false) String cursor) {
        throw notImplemented();
    }
}
