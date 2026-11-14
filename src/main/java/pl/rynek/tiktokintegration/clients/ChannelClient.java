package pl.rynek.tiktokintegration.clients;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

/** Kanał konta i dane kanałów do widoków filmów z channel-service ({@code /internal/channels/...}). */
@Component
public class ChannelClient {

    private final RestClient http;

    public ChannelClient(@Value("${rynek.channel.url}") String channelUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(channelUrl).requestFactory(requests).build();
    }

    /** Kanał konta albo pusty, gdy konto go nie ma. */
    public Optional<UUID> channelOf(UUID userId) {
        try {
            JsonNode channel = http.get().uri("/internal/channels/by-owner/{userId}", userId).retrieve().body(JsonNode.class);
            return channel == null ? Optional.empty() : Optional.of(UUID.fromString(channel.path("id").asString()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw unavailable();
        }
    }

    /** Kanały po id jako {@code ChannelRef} (id, handle, name, avatarUrl, verified); brakujące nie występują w mapie. */
    public Map<UUID, JsonNode> channels(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String joined = ids.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
        try {
            JsonNode found = http.get().uri(uri -> uri.path("/internal/channels").queryParam("ids", joined).build()).retrieve().body(JsonNode.class);
            Map<UUID, JsonNode> result = new HashMap<>();
            if (found != null) {
                found.forEach(c -> result.put(UUID.fromString(c.path("id").asString()), c));
            }
            return result;
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dane kanału są chwilowo niedostępne. Spróbuj ponownie za chwilę.");
    }
}
