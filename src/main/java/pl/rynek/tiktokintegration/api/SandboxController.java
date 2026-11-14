package pl.rynek.tiktokintegration.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import pl.rynek.tiktokintegration.connection.OauthState;
import pl.rynek.tiktokintegration.connection.OauthStateRepository;
import pl.rynek.tiktokintegration.provider.SandboxVideoProvider;
import pl.rynek.tiktokintegration.video.Video;
import pl.rynek.tiktokintegration.video.VideoService;

/**
 * Strony symulatora TikToka (tylko {@code rynek.tiktok.provider=sandbox}): zgoda OAuth (zastępuje stronę tiktok.com), odtwarzacz
 * osadzany w iframe (zastępuje {@code embed_link}) i okładki. „Zezwól” albo „Odmów” wraca na {@code redirect_uri} jak prawdziwy TikTok.
 * <p>
 * {@code <meta name="referrer" content="same-origin">}: gateway wysyła {@code Referrer-Policy: no-referrer}, a przy takiej polityce
 * przeglądarka wysyła formularz z {@code Origin: null}, który filtr CORS gatewaya odrzuca (403). Tak samo jak w symulatorze płatności.
 */
@RestController
@ConditionalOnProperty(name = "rynek.tiktok.provider", havingValue = SandboxVideoProvider.NAME)
public class SandboxController {

    private static final String STYLE = """
            body{font:16px/1.5 system-ui,sans-serif;background:#f4f5f7;color:#1b1d21;margin:0;display:grid;place-items:center;min-height:100vh}
            main{background:#fff;border-radius:16px;padding:24px;max-width:380px;width:calc(100%% - 32px);box-shadow:0 2px 12px #0001}
            h1{font-size:20px;margin:0 0 4px}.note{font-size:13px;color:#5b6170}ul{padding-left:20px}
            button{display:block;width:100%%;min-height:48px;border-radius:12px;font:inherit;font-weight:700;margin-top:10px;cursor:pointer}
            .ok{background:#1b1d21;color:#fff;border:0}.ghost{background:none;border:1px solid #c9ccd3}
            """;

    private final OauthStateRepository states;
    private final VideoService videos;

    public SandboxController(OauthStateRepository states, VideoService videos) {
        this.states = states;
        this.videos = videos;
    }

    @GetMapping(value = "/api/v1/tiktok/sandbox/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public String authorize(@RequestParam String state) {
        boolean known = states.existsById(state);
        String body = known
                ? """
                  <p>Aplikacja <b>Rynek</b> prosi o dostęp do Twojego konta TikTok:</p>
                  <ul><li>podstawowe dane konta,</li><li>lista Twoich publicznych filmów,</li><li>publikowanie filmów (post prywatny do czasu audytu).</li></ul>
                  <form method="post"><button name="result" value="allow" class="ok">Zezwól</button><button name="result" value="deny" class="ghost">Odmów</button></form>
                  """
                : "<p>To żądanie autoryzacji wygasło. Wróć do Rynku i połącz konto ponownie.</p>";
        return page("Symulator TikTok", "<h1>Symulator TikTok</h1><p class=\"note\">Środowisko testowe: nie łączymy się z prawdziwym TikTokiem.</p>" + body);
    }

    @PostMapping("/api/v1/tiktok/sandbox/authorize")
    public ResponseEntity<Void> decide(@RequestParam String state, @RequestParam String result) {
        Optional<OauthState> stored = states.findById(state);
        String query = "allow".equals(result) && stored.isPresent()
                ? "code=" + SandboxVideoProvider.CODE_PREFIX + stored.get().getUserId() + "&state=" + state
                : "error=access_denied&state=" + state;
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, URI.create("/api/v1/tiktok/callback?" + query).toString()).build();
    }

    /** Odtwarzacz w iframe: tytuł, czas i „film” w kolorze okładki (prawdziwy embed TikToka dostajemy dopiero po audycie i kluczach). */
    @GetMapping(value = "/api/v1/tiktok/sandbox/player/{externalId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> player(@PathVariable String externalId) {
        Optional<Video> video = videos.byExternalId(externalId);
        if (video.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(page("Film niedostępny", "<h1>Film niedostępny</h1>"));
        }
        Video v = video.get();
        String html = """
                <!doctype html><html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>
                html,body{height:100%%;margin:0}body{display:grid;place-items:center;background:%s;color:#fff;font:16px/1.4 system-ui,sans-serif;text-align:center}
                .play{width:72px;height:72px;border-radius:50%%;border:0;background:#fff;cursor:pointer;display:grid;place-items:center}
                .play::before{content:"";border-style:solid;border-width:14px 0 14px 24px;border-color:transparent transparent transparent #1b1d21;margin-left:6px}
                .t{font-weight:700;margin:16px 16px 4px}.m{font-size:13px;opacity:.85}
                </style></head><body><div><button class="play" aria-label="Odtwórz film"></button>
                <div class="t">%s</div><div class="m">%s symulator odtwarzacza TikTok</div></div></body></html>
                """.formatted(HtmlUtils.htmlEscape(v.getTitle()), color(v.getExternalId()), HtmlUtils.htmlEscape(v.getTitle()),
                v.getDurationSec() > 0 ? v.getDurationSec() + " s ·" : "");
        return ResponseEntity.ok().body(html);
    }

    @GetMapping("/api/v1/tiktok/sandbox/cover/{externalId}")
    public ResponseEntity<byte[]> cover(@PathVariable String externalId) {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"270\" height=\"480\" viewBox=\"0 0 270 480\"><rect width=\"270\" height=\"480\" fill=\""
                + color(externalId) + "\"/><circle cx=\"135\" cy=\"240\" r=\"36\" fill=\"#fff\"/><path d=\"M126 222l26 18-26 18z\" fill=\"#1b1d21\"/></svg>";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml")).cacheControl(CacheControl.noStore())
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static String color(String id) {
        String[] palette = {"#c0392b", "#2471a3", "#1e8449", "#7d3c98", "#b9770e", "#117a65"};
        return palette[Math.floorMod(id.hashCode(), palette.length)];
    }

    private static String page(String title, String content) {
        return """
               <!doctype html><html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
               <meta name="referrer" content="same-origin"><title>%s</title><style>%s</style></head><body><main>%s</main></body></html>
               """.formatted(title, STYLE.formatted(), content);
    }
}
