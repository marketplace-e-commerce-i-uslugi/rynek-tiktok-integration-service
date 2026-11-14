package pl.rynek.tiktokintegration.provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Symulator TikToka (tylko {@code rynek.tiktok.provider=sandbox}): zgoda OAuth na własnej stronie ({@code SandboxController}), stałe
 * filmy „konta”, odtwarzacz i okładki z własnych adresów, publikacja zawsze prywatna (jak aplikacja przed audytem). Link do filmu
 * czytamy z samego adresu, bez połączenia z internetem. Tokeny niosą identyfikator konta, więc dostawca nie ma stanu.
 */
@Component
@ConditionalOnProperty(name = "rynek.tiktok.provider", havingValue = SandboxVideoProvider.NAME)
public class SandboxVideoProvider implements VideoProvider {

    public static final String NAME = "sandbox";
    public static final String CODE_PREFIX = "sandbox-";
    private static final List<String> SCOPES = List.of("user.info.basic", "video.list", "video.upload", "video.publish");
    private static final Pattern LINK = Pattern.compile("^https?://(?:www\\.)?tiktok\\.com/@([A-Za-z0-9._]{1,64})/video/(\\d{6,25})(?:[/?#].*)?$");
    private static final String[] TITLES = {"Unboxing nowych słuchawek", "Test wiertarki udarowej", "Jak wybrać laptop do pracy",
            "Naprawa komputera krok po kroku", "Montaż mebli: poradnik", "Malowanie ścian bez smug", "Recenzja smartfona po tygodniu",
            "Paczka od sprzedawcy: co w środku"};
    private static final int[] DURATIONS = {24, 58, 95, 41, 33, 62, 77, 19};

    private final String publicUrl;

    public SandboxVideoProvider(@Value("${rynek.tiktok.public-url}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    @Override
    public String authorizeUrl(String state) {
        return publicUrl + "/api/v1/tiktok/sandbox/authorize?state=" + state;
    }

    /** Kod ma postać {@code sandbox-<userId>}; konto TikToka ({@code open_id}) wynika z niego deterministycznie. */
    @Override
    public Tokens exchange(String code) {
        if (code == null || !code.startsWith(CODE_PREFIX) || code.length() == CODE_PREFIX.length()) {
            throw new ProviderException("TikTok odrzucił kod autoryzacji.");
        }
        return tokens("sbx_" + Integer.toHexString(code.hashCode()));
    }

    @Override
    public Tokens refresh(String refreshToken) {
        if (refreshToken == null || !refreshToken.startsWith("sbx-rt-")) {
            throw new ProviderException("TikTok odrzucił token odświeżania.");
        }
        return tokens(openIdOf(refreshToken));
    }

    @Override
    public void revoke(String accessToken) {
        // Symulator nie przechowuje tokenów, więc nie ma czego unieważniać.
    }

    @Override
    public Account account(String accessToken) {
        return new Account("Sklep na TikToku " + openIdOf(accessToken).substring(4));
    }

    @Override
    public VideoPage listVideos(String accessToken, String cursor, int maxCount) {
        String openId = openIdOf(accessToken);
        int from = cursor == null || cursor.isBlank() ? 0 : Integer.parseInt(cursor);
        List<ProviderVideo> items = new ArrayList<>();
        int end = Math.min(TITLES.length, from + Math.max(1, maxCount));
        for (int i = from; i < end; i++) {
            String id = openId + "-v" + (i + 1);
            boolean vertical = i % 4 != 3;
            items.add(new ProviderVideo(id, TITLES[i], DURATIONS[i], publicUrl + "/api/v1/tiktok/sandbox/cover/" + id,
                    "/api/v1/tiktok/sandbox/player/" + id, vertical, 120L + Math.floorMod((openId + i).hashCode(), 9000), i % 2 == 0));
        }
        return new VideoPage(items, end < TITLES.length ? String.valueOf(end) : null);
    }

    @Override
    public Oembed oembed(String link) {
        Matcher m = link == null ? null : LINK.matcher(link.trim());
        if (m == null || !m.matches()) {
            throw new ProviderException("Podaj link do filmu TikTok, np. https://www.tiktok.com/@konto/video/1234567890123456789.");
        }
        // Symulator nie łączy się z prawdziwym tiktok.com (id z linku bywa fałszywe), więc film z linku gra w odtwarzaczu symulatora.
        return new Oembed(m.group(2), "Film @" + m.group(1), "/api/v1/tiktok/sandbox/player/" + m.group(2), null);
    }

    @Override
    public String initPost(String accessToken, String title) {
        openIdOf(accessToken);
        return "sbx-pub-" + UUID.randomUUID();
    }

    /** Aplikacja przed audytem publikuje tylko prywatnie (§0, punkt 4). */
    @Override
    public PostState postStatus(String accessToken, String publishId) {
        return publishId != null && publishId.startsWith("sbx-pub-") ? PostState.PRIVATE_ONLY : PostState.FAILED;
    }

    private static Tokens tokens(String openId) {
        return new Tokens("sbx-at-" + openId + "-" + UUID.randomUUID(), "sbx-rt-" + openId + "-" + UUID.randomUUID(),
                Duration.ofHours(24), Duration.ofDays(365), openId, SCOPES);
    }

    /** Token ma postać {@code sbx-at-sbx_<hex>-<uuid>}; konto to część {@code sbx_<hex>}. */
    private static String openIdOf(String token) {
        if (token == null || !(token.startsWith("sbx-at-") || token.startsWith("sbx-rt-"))) {
            throw new ProviderException("TikTok odrzucił token dostępu.");
        }
        String rest = token.substring(7);
        int dash = rest.indexOf('-');
        if (dash < 0) {
            throw new ProviderException("TikTok odrzucił token dostępu.");
        }
        return rest.substring(0, dash);
    }
}
