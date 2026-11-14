package pl.rynek.tiktokintegration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.rynek.tiktokintegration.TestcontainersConfig;
import pl.rynek.tiktokintegration.clients.CatalogClient;
import pl.rynek.tiktokintegration.clients.ChannelClient;
import pl.rynek.tiktokintegration.common.WebhookSignature;
import tools.jackson.databind.json.JsonMapper;

/** Połączenie konta, filmy, link, publikacja i webhook na prawdziwym PostgreSQL i symulatorze TikToka; channel i catalog to atrapy. */
@SpringBootTest(properties = "rynek.tiktok.jobs-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class TiktokFlowTests {

    private static final String LINK = "https://www.tiktok.com/@sklep/video/7300000000000000001";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired WebhookSignature signature;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ChannelClient channels;
    @MockitoBean CatalogClient catalog;

    private final Map<UUID, UUID> offerChannels = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(channels.channelOf(any())).willAnswer(call -> {
            UUID user = call.getArgument(0);
            return noChannel.contains(user) ? Optional.empty() : Optional.of(channelOf(user));
        });
        given(channels.channels(any())).willAnswer(call -> {
            Map<UUID, tools.jackson.databind.JsonNode> map = new ConcurrentHashMap<>();
            for (UUID id : (java.util.Collection<UUID>) call.getArgument(0)) {
                map.put(id, json.readTree("{\"id\":\"" + id + "\",\"handle\":\"sklep\",\"name\":\"Sklep\",\"verified\":false}"));
            }
            return map;
        });
        given(catalog.channelOfOffer(any())).willAnswer(call -> Optional.ofNullable(offerChannels.get((UUID) call.getArgument(0))));
    }

    private final java.util.Set<UUID> noChannel = ConcurrentHashMap.newKeySet();

    @Test
    void panelSprzedawcyWymagaLogowaniaIKanalu() throws Exception {
        mvc.perform(get("/api/v1/tiktok/connection")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tiktok/connect/url")).andExpect(status().isUnauthorized());
        UUID bezKanalu = UUID.randomUUID();
        noChannel.add(bezKanalu);
        mvc.perform(get("/api/v1/tiktok/connect/url").header(HttpHeaders.AUTHORIZATION, bearer(bezKanalu))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("Najpierw załóż kanał.")));
    }

    @Test
    void polaczenieKonta_zgodaTokenyZaszyfrowaneIFilmyKonta() throws Exception {
        UUID seller = UUID.randomUUID();
        mvc.perform(get("/api/v1/tiktok/connection").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.connected", is(false))).andExpect(jsonPath("$.scopes", hasSize(0)));

        connect(seller);

        mvc.perform(get("/api/v1/tiktok/connection").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.connected", is(true))).andExpect(jsonPath("$.displayName", startsWith("Sklep na TikToku")))
                .andExpect(jsonPath("$.scopes", hasItem("video.publish"))).andExpect(jsonPath("$.lastSyncAt").isNotEmpty());
        String access = jdbc.queryForObject("select access_token from tiktok_connections where user_id = ?", String.class, seller);
        assertThat(access).doesNotContain("sbx-at").doesNotContain("sbx_");
        // Pierwsza synchronizacja przy łączeniu: 8 filmów konta (6 pionowych, 2 poziome).
        mvc.perform(get("/api/v1/tiktok/me/videos").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.items", hasSize(8))).andExpect(jsonPath("$.nextCursor", nullValue()))
                .andExpect(jsonPath("$.items[0].externalVideoId").isNotEmpty()).andExpect(jsonPath("$.items[0].title").isNotEmpty());
        mvc.perform(get("/api/v1/tiktok/me/videos").param("limit", "3").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.items", hasSize(3))).andExpect(jsonPath("$.nextCursor", is("3")));

        // Ręczna synchronizacja nie dubluje filmów.
        mvc.perform(post("/api/v1/tiktok/sync").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tiktok/me/videos").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(jsonPath("$.items", hasSize(8)));

        mvc.perform(delete("/api/v1/tiktok/connection").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tiktok/connection").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(jsonPath("$.connected", is(false)));
        mvc.perform(post("/api/v1/tiktok/sync").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isConflict());
    }

    @Test
    void stateJestJednorazowyAOdmowaWracaZKomunikatem() throws Exception {
        UUID seller = UUID.randomUUID();
        String state = stateOf(connectUrl(seller));
        mvc.perform(post("/api/v1/tiktok/sandbox/authorize").param("state", state).param("result", "deny")).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", containsString("error=access_denied")));
        mvc.perform(get("/api/v1/tiktok/callback").param("error", "access_denied").param("state", state)).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", is("http://localhost:4200/sprzedawca/filmy?tiktok=denied")));
        // state zużyty: drugi powrót z tym samym parametrem to błąd, a obcy state też.
        mvc.perform(get("/api/v1/tiktok/callback").param("code", "sandbox-" + seller).param("state", state)).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", is("http://localhost:4200/sprzedawca/filmy?tiktok=error")));
        mvc.perform(get("/api/v1/tiktok/callback").param("code", "sandbox-x").param("state", "obcy")).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", is("http://localhost:4200/sprzedawca/filmy?tiktok=error")));
        mvc.perform(get("/api/v1/tiktok/connection").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(jsonPath("$.connected", is(false)));
    }

    @Test
    void przypisanieFilmuKontaDoOferty_tylkoWlasnegoKanalu() throws Exception {
        UUID seller = UUID.randomUUID();
        connect(seller);
        String external = JsonPath.read(mvc.perform(get("/api/v1/tiktok/me/videos").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andReturn().getResponse().getContentAsString(), "$.items[0].externalVideoId");
        UUID mine = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        offerChannels.put(mine, channelOf(seller));
        offerChannels.put(foreign, channelOf(UUID.randomUUID()));

        mvc.perform(post("/api/v1/tiktok/me/videos/" + external + "/attach").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + foreign + "\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tiktok/me/videos/" + external + "/attach").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tiktok/me/videos/nieznany/attach").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + mine + "\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tiktok/me/videos/" + external + "/attach").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        String videoId = JsonPath.read(mvc.perform(post("/api/v1/tiktok/me/videos/" + external + "/attach").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + mine + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.offerId", is(mine.toString()))).andExpect(jsonPath("$.channel.name", is("Sklep")))
                .andExpect(jsonPath("$.tone").isNumber()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/offers/" + mine + "/video")).andExpect(status().isOk()).andExpect(jsonPath("$.id", is(videoId)))
                .andExpect(jsonPath("$.embedUrl", startsWith("/api/v1/tiktok/sandbox/player/"))).andExpect(jsonPath("$.durationSec").isNumber());
        mvc.perform(get("/api/v1/videos/" + videoId)).andExpect(status().isOk()).andExpect(jsonPath("$.title").isNotEmpty());
        mvc.perform(get("/internal/videos").param("offerIds", mine + "," + UUID.randomUUID() + ",zly")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].videoId", is(videoId))).andExpect(jsonPath("$[0].durationSec").isNumber());

        // Oferta ma najwyżej jeden film: drugi film z konta zdejmuje pierwszy.
        String second = JsonPath.read(mvc.perform(get("/api/v1/tiktok/me/videos").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andReturn().getResponse().getContentAsString(), "$.items[1].externalVideoId");
        mvc.perform(post("/api/v1/tiktok/me/videos/" + second + "/attach").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":\"" + mine + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/offers/" + mine + "/video")).andExpect(jsonPath("$.id", org.hamcrest.Matchers.not(videoId)));
    }

    @Test
    void filmZJednejOfertyNieSkaczeDoDrugiejPoCichu_chybaZeTamtejJuzNieMa() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        offerChannels.put(first, channelOf(seller));
        offerChannels.put(second, channelOf(seller));
        String body1 = "{\"link\":\"https://www.tiktok.com/@sklep/video/7300000000000000003\",\"offerId\":\"" + first + "\"}";
        String body2 = "{\"link\":\"https://www.tiktok.com/@sklep/video/7300000000000000003\",\"offerId\":\"" + second + "\"}";
        String video = JsonPath.read(mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content(body1)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        // Ten sam film do tej samej oferty: bez zmian. Do drugiej: 409, a pierwsza zachowuje film.
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", startsWith("Ten film jest już przypisany do innej oferty")));
        mvc.perform(get("/api/v1/offers/" + first + "/video")).andExpect(jsonPath("$.id", is(video)));
        mvc.perform(get("/api/v1/offers/" + second + "/video")).andExpect(status().isNotFound());
        // Po zdjęciu z pierwszej oferty film można przypisać do drugiej.
        mvc.perform(put("/internal/offers/" + first + "/video").contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelId\":\"" + channelOf(seller) + "\",\"videoId\":null}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isOk());
        // Oferta, której już nie ma w catalog, nie blokuje filmu.
        offerChannels.remove(second);
        UUID third = UUID.randomUUID();
        offerChannels.put(third, channelOf(seller));
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"https://www.tiktok.com/@sklep/video/7300000000000000003\",\"offerId\":\"" + third + "\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/offers/" + third + "/video")).andExpect(jsonPath("$.id", is(video)));
    }

    @Test
    void filmPoLinku_walidacjaIBezPolaczeniaKonta() throws Exception {
        UUID seller = UUID.randomUUID();
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"https://example.com/film\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", startsWith("Podaj link do filmu TikTok")));
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        UUID offer = UUID.randomUUID();
        offerChannels.put(offer, channelOf(seller));
        String first = JsonPath.read(mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"link\":\"" + LINK + "\",\"offerId\":\"" + offer + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.embedUrl", is("/api/v1/tiktok/sandbox/player/7300000000000000001")))
                .andExpect(jsonPath("$.offerId", is(offer.toString()))).andReturn().getResponse().getContentAsString(), "$.id");
        // Ten sam link ponownie: ten sam film.
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"" + LINK + "\"}")).andExpect(jsonPath("$.id", is(first)));
        // Cudza oferta przy dodawaniu linku: 403.
        UUID cudza = UUID.randomUUID();
        offerChannels.put(cudza, channelOf(UUID.randomUUID()));
        mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"" + LINK + "\",\"offerId\":\"" + cudza + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void filmyKanaluIShortsSaPubliczne_ShortsTylkoPionowe() throws Exception {
        UUID seller = UUID.randomUUID();
        connect(seller);
        mvc.perform(get("/api/v1/channels/" + channelOf(seller) + "/videos")).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(8)));
        mvc.perform(get("/api/v1/channels/" + channelOf(seller) + "/videos").param("limit", "5")).andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.nextCursor", is("5")));
        mvc.perform(get("/api/v1/channels/" + channelOf(seller) + "/videos").param("cursor", "zly")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/channels/" + UUID.randomUUID() + "/videos")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/videos/shorts").param("limit", "50")).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].vertical", org.hamcrest.Matchers.everyItem(is(true))));
        mvc.perform(get("/api/v1/videos/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/videos/nie-uuid")).andExpect(status().isNotFound());
    }

    @Test
    void ofertaDlaCatalog_zapisIOdpiecieFilmu() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID offer = UUID.randomUUID();
        offerChannels.put(offer, channelOf(seller));
        String video = JsonPath.read(mvc.perform(post("/api/v1/videos/by-link").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"link\":\"https://www.tiktok.com/@sklep/video/7300000000000000002\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/internal/offers/" + offer + "/video").contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelId\":\"" + channelOf(other) + "\",\"videoId\":\"" + video + "\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/internal/offers/" + offer + "/video").contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelId\":\"" + channelOf(seller) + "\",\"videoId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isNotFound());
        mvc.perform(put("/internal/offers/" + offer + "/video").contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelId\":\"" + channelOf(seller) + "\",\"videoId\":\"" + video + "\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/offers/" + offer + "/video")).andExpect(jsonPath("$.id", is(video)));
        mvc.perform(put("/internal/offers/" + offer + "/video").contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelId\":\"" + channelOf(seller) + "\",\"videoId\":null}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/offers/" + offer + "/video")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/offers/zly/video")).andExpect(status().isNotFound());
    }

    @Test
    void publikacjaNaTiktok_prywatnaDoAudytuIWebhookZPodpisem() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID offer = UUID.randomUUID();
        offerChannels.put(offer, channelOf(seller));
        String body = "{\"offerId\":\"" + offer + "\",\"title\":\"Nowy film\"}";
        mvc.perform(post("/api/v1/tiktok/posts").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        connect(seller);
        UUID foreign = UUID.randomUUID();
        offerChannels.put(foreign, channelOf(UUID.randomUUID()));
        mvc.perform(post("/api/v1/tiktok/posts").header(HttpHeaders.AUTHORIZATION, bearer(seller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"offerId\":\"" + foreign + "\",\"title\":\"Cudza\"}")).andExpect(status().isForbidden());
        String publishId = JsonPath.read(mvc.perform(post("/api/v1/tiktok/posts").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("PROCESSING")))
                .andReturn().getResponse().getContentAsString(), "$.publishId");
        mvc.perform(get("/api/v1/tiktok/posts/" + publishId + "/status").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.status", is("PRIVATE_ONLY")));
        mvc.perform(get("/api/v1/tiktok/posts/" + publishId + "/status").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        String event = "{\"publishId\":\"" + publishId + "\",\"status\":\"PUBLISHED\"}";
        mvc.perform(post("/api/v1/tiktok/webhooks").contentType(MediaType.APPLICATION_JSON).content(event)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tiktok/webhooks").contentType(MediaType.APPLICATION_JSON).header("X-Signature", "zly").content(event))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tiktok/webhooks").contentType(MediaType.APPLICATION_JSON).header("X-Signature", signature.sign(event)).content(event))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tiktok/posts/" + publishId + "/status").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.status", is("PUBLISHED")));
        String bad = "{\"publishId\":\"" + publishId + "\",\"status\":\"CO?\"}";
        mvc.perform(post("/api/v1/tiktok/webhooks").contentType(MediaType.APPLICATION_JSON).header("X-Signature", signature.sign(bad)).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stronySymulatora_zgodaOdtwarzaczIOkladka() throws Exception {
        UUID seller = UUID.randomUUID();
        String state = stateOf(connectUrl(seller));
        mvc.perform(get("/api/v1/tiktok/sandbox/authorize").param("state", state)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Zezwól"))).andExpect(content().string(containsString("Rynek")));
        mvc.perform(get("/api/v1/tiktok/sandbox/authorize").param("state", "wygasl")).andExpect(content().string(containsString("wygasło")));
        connect(seller);
        String external = JsonPath.read(mvc.perform(get("/api/v1/tiktok/me/videos").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andReturn().getResponse().getContentAsString(), "$.items[0].externalVideoId");
        mvc.perform(get("/api/v1/tiktok/sandbox/player/" + external)).andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN")).andExpect(content().string(containsString("Odtwórz film")));
        mvc.perform(get("/api/v1/tiktok/sandbox/player/brak")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tiktok/sandbox/cover/" + external)).andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"));
    }

    // --- pomocnicze ---

    /** Pełna zgoda: adres autoryzacji, „Zezwól” na stronie symulatora i powrót na callback. */
    private void connect(UUID seller) throws Exception {
        String state = stateOf(connectUrl(seller));
        MvcResult decided = mvc.perform(post("/api/v1/tiktok/sandbox/authorize").param("state", state).param("result", "allow"))
                .andExpect(status().isSeeOther()).andReturn();
        String location = decided.getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/v1/tiktok/callback?code=sandbox-" + seller);
        mvc.perform(get(location)).andExpect(status().isSeeOther())
                .andExpect(header().string("Location", is("http://localhost:4200/sprzedawca/filmy?tiktok=connected")));
    }

    private String connectUrl(UUID seller) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/tiktok/connect/url").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.url");
    }

    private static String stateOf(String url) {
        assertThat(url).startsWith("http://localhost:8080/api/v1/tiktok/sandbox/authorize?state=");
        return url.substring(url.indexOf("state=") + 6);
    }

    private static UUID channelOf(UUID owner) {
        return UUID.nameUUIDFromBytes(("kanal-" + owner).getBytes());
    }

    private static String bearer(UUID userId) {
        return "Bearer user-" + userId;
    }
}
