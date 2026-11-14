package pl.rynek.tiktokintegration.provider;

import java.time.Duration;
import java.util.List;

/**
 * Dostawca wideo za interfejsem (zalecenie z §0): dziś symulator {@link SandboxVideoProvider}, docelowo TikTok (Display API, Content
 * Posting API, oEmbed), a w razie zmiany zasad TikToka własny hosting. Pozostałe serwisy o dostawcy nic nie wiedzą.
 */
public interface VideoProvider {

    /** Adres, na który przeglądarka sprzedawcy idzie po zgodę (parametr {@code state} chroni przed CSRF). */
    String authorizeUrl(String state);

    /** Wymiana kodu z powrotu OAuth na tokeny. {@code code} pochodzi od dostawcy. */
    Tokens exchange(String code);

    Tokens refresh(String refreshToken);

    void revoke(String accessToken);

    Account account(String accessToken);

    /** Publiczne filmy konta, strona po {@code maxCount} (TikTok: do 20) z kursorem dostawcy. */
    VideoPage listVideos(String accessToken, String cursor, int maxCount);

    /** Film po linku bez połączenia konta (oEmbed). Zły link daje {@link ProviderException}. */
    Oembed oembed(String link);

    /** Rozpoczyna publikację; zwraca {@code publishId}. */
    String initPost(String accessToken, String title);

    PostState postStatus(String accessToken, String publishId);

    record Tokens(String accessToken, String refreshToken, Duration accessTtl, Duration refreshTtl, String openId, List<String> scopes) {
    }

    record Account(String displayName) {
    }

    record ProviderVideo(String externalId, String title, int durationSec, String coverUrl, String embedUrl, boolean vertical,
                         long viewCount, boolean captions) {
    }

    record VideoPage(List<ProviderVideo> items, String nextCursor) {
    }

    record Oembed(String externalId, String title, String embedUrl, String coverUrl) {
    }

    enum PostState { PROCESSING, PUBLISHED, FAILED, PRIVATE_ONLY }

    /** Błąd po stronie dostawcy (zły kod, link, odrzucone żądanie); wychodzi jako 400 z treścią po polsku. */
    class ProviderException extends RuntimeException {
        public ProviderException(String message) {
            super(message);
        }
    }
}
