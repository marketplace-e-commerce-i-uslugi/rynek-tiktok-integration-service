package pl.rynek.tiktokintegration.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.tiktokintegration.api.dto.AttachVideoToOfferRequest;
import pl.rynek.tiktokintegration.api.dto.CreatePostRequest;

import static pl.rynek.tiktokintegration.common.NotImplemented.notImplemented;

@RestController
public class TiktokController {

    /**
     * Zwraca adres autoryzacji TikTok z parametrem state (CSRF). Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/connect/url")
    public void getConnectUrl() {
        throw notImplemented();
    }

    /**
     * redirect_uri zarejestrowany w TikTok: wymiana code na tokeny, zapis zaszyfrowany. Dostęp: pub (z state).
     */
    @GetMapping("/api/v1/tiktok/callback")
    public void handleOAuthCallback() {
        throw notImplemented();
    }

    /**
     * Status połączenia (połączone, wygasa, zakres uprawnień). Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/connection")
    public void getConnection() {
        throw notImplemented();
    }

    /**
     * Rozłączenie konta + unieważnienie tokenu. Dostęp: seller.
     */
    @DeleteMapping("/api/v1/tiktok/connection")
    public void disconnect() {
        throw notImplemented();
    }

    /**
     * Ręczna synchronizacja filmów. Dostęp: seller.
     */
    @PostMapping("/api/v1/tiktok/sync")
    public void sync() {
        throw notImplemented();
    }

    /**
     * Filmy z połączonego konta do wyboru w kreatorze oferty. Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/me/videos")
    public void listMyVideos(@RequestParam(required = false) String cursor) {
        throw notImplemented();
    }

    /**
     * Przypisanie filmu do oferty (offerId). Dostęp: seller.
     */
    @PostMapping("/api/v1/tiktok/me/videos/{externalVideoId}/attach")
    public void attachVideoToOffer(@PathVariable String externalVideoId, @RequestBody AttachVideoToOfferRequest request) {
        throw notImplemented();
    }

    /**
     * Publikacja filmu z Rynku na TikTok: init posta, zwraca publishId i dane wysyłki. Dostęp: seller.
     */
    @PostMapping("/api/v1/tiktok/posts")
    public void createPost(@RequestBody CreatePostRequest request) {
        throw notImplemented();
    }

    /**
     * Status publikacji. Dostęp: seller.
     */
    @GetMapping("/api/v1/tiktok/posts/{publishId}/status")
    public void getPostStatus(@PathVariable String publishId) {
        throw notImplemented();
    }

    /**
     * Odbiornik zdarzeń TikTok (publikacja zakończona/nieudana, film publiczny). Dostęp: pub (podpis).
     */
    @PostMapping("/api/v1/tiktok/webhooks")
    public void handleWebhook(@RequestBody String payload) {
        throw notImplemented();
    }
}
