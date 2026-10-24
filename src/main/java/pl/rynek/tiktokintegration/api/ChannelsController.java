package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.tiktokintegration.common.NotImplemented.notImplemented;

@RestController
public class ChannelsController {

    /**
     * Zakładka „Filmy” kanału. Dostęp: pub.
     */
    @GetMapping("/api/v1/channels/{channelId}/videos")
    public void getVideos(@PathVariable String channelId, @RequestParam(required = false) String cursor) {
        throw notImplemented();
    }
}
