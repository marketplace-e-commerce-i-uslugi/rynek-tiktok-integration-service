package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.tiktokintegration.common.NotImplemented.notImplemented;

@RestController
public class InternalVideosController {

    /**
     * Wsadowo: filmy i okładki dla kart ofert. Dostęp: int.
     */
    @GetMapping("/internal/videos")
    public void getVideosForOffers(@RequestParam(required = false) String offerIds) {
        throw notImplemented();
    }
}
