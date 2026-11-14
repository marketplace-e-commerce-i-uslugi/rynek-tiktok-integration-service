package pl.rynek.tiktokintegration.clients;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Do kogo należy oferta: potrzebne, żeby sprzedawca podpinał filmy tylko do ofert własnego kanału (także do szkiców). */
@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(@Value("${rynek.catalog.url}") String catalogUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(catalogUrl).requestFactory(requests).build();
    }

    /** Kanał oferty (w tym szkicu) albo pusty, gdy oferty nie ma. */
    public Optional<UUID> channelOfOffer(UUID offerId) {
        try {
            JsonNode body = http.get().uri("/internal/offers/{offerId}/channel", offerId).retrieve().body(JsonNode.class);
            return body == null ? Optional.empty() : Optional.of(UUID.fromString(body.path("channelId").asString()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dane oferty są chwilowo niedostępne. Spróbuj ponownie za chwilę.");
        }
    }
}
