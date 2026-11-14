package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.tiktokintegration.api.Dtos.VideoView;
import pl.rynek.tiktokintegration.common.Cursors.Page;
import pl.rynek.tiktokintegration.video.VideoService;

@RestController
public class ChannelsController {

    private final VideoService videos;

    public ChannelsController(VideoService videos) {
        this.videos = videos;
    }

    /**
     * Zakładka „Filmy” kanału (ścieżka pod prefiksem {@code /channels}, routowana w gateway do tego serwisu). Dostęp: pub.
     */
    @GetMapping("/api/v1/channels/{channelId}/videos")
    public Page<VideoView> getChannelVideos(@PathVariable String channelId, @RequestParam(required = false) String cursor,
                                            @RequestParam(required = false) Integer limit) {
        return videos.ofChannel(VideosController.uuid(channelId, "Nie znaleziono kanału."), cursor, limit);
    }
}
