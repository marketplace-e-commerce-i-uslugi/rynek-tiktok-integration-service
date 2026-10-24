package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.tiktokintegration.common.NotImplemented.notImplemented;

@RestController
public class OffersController {

    /**
     * Film przypisany do oferty. Dostęp: pub.
     */
    @GetMapping("/api/v1/offers/{offerId}/video")
    public void getVideo(@PathVariable String offerId) {
        throw notImplemented();
    }
}
