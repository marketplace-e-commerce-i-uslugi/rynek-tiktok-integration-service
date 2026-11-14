# rynek-tiktok-integration-service

`tiktok-integration-service` — TikTok (konto, filmy, publikowanie)

> Status: **działa na symulatorze `sandbox`** (PostgreSQL, Flyway, JWT z JWKS, bez Kafki). Prawdziwego dostawcy TikTok nie ma, bo wymaga `client_key` i `client_secret` z konsoli deweloperskiej oraz audytu aplikacji; jest za interfejsem `VideoProvider`, więc dojdzie bez zmian w reszcie systemu.

- **Połączenie konta:** `connect/url` zwraca adres strony zgody symulatora (z jednorazowym, 10-minutowym `state`), „Zezwól” wraca na `callback`, który wymienia kod na tokeny (zapis zaszyfrowany AES-GCM kluczem `TIKTOK_TOKEN_KEY`), robi pierwszą synchronizację i przekierowuje na `/sprzedawca/filmy?tiktok=connected|denied|error`.
- **Filmy konta:** symulator oddaje 8 stałych filmów na konto (6 pionowych, 2 poziome). Synchronizacja (ręczna, przy łączeniu i co 12 h) dodaje, odświeża i oznacza usunięte (`REMOVED` znika z odtwarzania i Shortów; oferta zachowuje `videoId` i pokazuje się bez filmu).
- **Przypisanie do oferty:** `attach` i `by-link` sprawdzają w catalog, że oferta (także szkic) jest z kanału sprzedawcy; oferta ma najwyżej jeden film, a film jedną ofertę. Catalog przy zapisie `videoId` woła `PUT /internal/offers/{id}/video`.
- **Link:** `by-link` przyjmuje `https://www.tiktok.com/@konto/video/<id>` (sandbox czyta link bez internetu); w sandboxie `embedUrl` to odtwarzacz symulatora (id z linku bywa fałszywe, a symulator nie łączy się z tiktok.com), a prawdziwy dostawca da `https://www.tiktok.com/embed/v2/<id>`. Ten sam link zwraca ten sam film. Film z linku nie ma znanej długości (`durationSec` 0), więc karta oferty nie pokazuje czasu.
- **Odtwarzacz:** `embedUrl` filmów konta w sandboxie to `/api/v1/tiktok/sandbox/player/{externalId}` (strona w iframe, ramki dozwolone z tej samej domeny: `X-Frame-Options: SAMEORIGIN` w serwisie i gateway). Okładka to `/sandbox/cover/{externalId}` (SVG).
- **Publikacja:** `posts` wymaga połączenia ze zgodą `video.publish` i oferty własnego kanału; status sandboxu to `PRIVATE_ONLY` (aplikacja przed audytem). Webhook `POST /tiktok/webhooks` z podpisem HMAC (`X-Signature`, sekret `TIKTOK_WEBHOOK_SECRET`) zmienia status posta.
- **Zadania w tle** (`rynek.tiktok.jobs-enabled`): co 10 min odświeżają tokeny wygasające w ciągu godziny i synchronizują konta nieodświeżane od 12 h.
- **Bez:** zapisu nagranego filmu przez `mediaId` (pole przyjęte, ale nieużywane), oznaczania ofert po usunięciu filmu i limitów zapytań TikToka (6/min na init publikacji).

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8085, zmienna PORT; baza `tiktok` z rynek-infra/local
./mvnw verify            # testy
```

- Swagger UI: http://localhost:8085/swagger-ui.html
- Zdrowie: http://localhost:8085/actuator/health

## Ograniczenia TikTok API (§0 dokumentu architektury)

Z dokumentacji TikTok for Developers (sprawdzone w tej sesji) wynika kilka ograniczeń, które wpływają na produkt:

1. **Display API zwraca tylko filmy zalogowanego (autoryzującego) użytkownika.** `POST /v2/video/list/` oddaje publiczne filmy właściciela tokenu, a `POST /v2/video/query/` potwierdza, że filmy należą do tego użytkownika. Nie znalazłem w dokumentacji opcji pobierania filmów dowolnych innych kont. Wniosek: **sprzedawca musi połączyć własne konto TikTok z Rynkiem**, a Rynek pokazuje jego filmy.
2. **Linki do okładek wygasają.** `cover_image_url` ma parametr `x-expires`; dokumentacja każe odświeżać go przez `video/query`. Czas życia nie jest podany — zakładaj, że krótki, i odświeżaj cyklicznie. TikTok zaleca też synchronizację filmów użytkownika co 12 godzin.
3. **Odtwarzanie to osadzenie (`embed_link` / `embed_html`)**, nie surowy plik wideo. Podgląd wyciszony „po najechaniu” z design systemu nie jest więc gwarantowany — bezpieczniej: okładka w feedzie, odtwarzanie po tapnięciu. Napisy do filmów (wymóg z README) kontroluje TikTok, nie Rynek.
4. **Publikowanie z Rynku na TikTok (Content Posting API)** wymaga zatwierdzenia scope `video.publish`. Dopóki aplikacja nie przejdzie audytu, **wszystkie posty są prywatne**. Dla `PULL_FROM_URL` domena musi być zweryfikowana.
5. **Tokeny:** access token ważny 24 h, refresh token 365 dni. Serwis musi odświeżać je w tle; `client_secret` i tokeny tylko po stronie backendu, szyfrowane w bazie.
6. **Ścieżka awaryjna bez OAuth:** publiczny `oEmbed` (`GET https://www.tiktok.com/oembed?url=...`) pozwala osadzić film po samym linku — sprzedawca wkleja link. Dokumentacja nie podaje tu limitów.
7. **Nie potwierdziłem:** limitów zapytań (rate limit), czasu wygaśnięcia okładek i `embed_link`, dziennego limitu postów, limitów rozmiaru plików. Sprawdź w konsoli deweloperskiej TikTok. Przejrzyj też regulamin TikTok pod kątem użycia w marketplace — to decyzja produktowo-prawna, nie techniczna.

**Zalecenie architektoniczne:** schowaj TikToka za interfejsem `VideoProvider` w `tiktok-integration-service`, żeby w razie zmiany zasad dało się dodać własny hosting wideo bez zmian w pozostałych serwisach.

**Wpływ na design:** krok „Zdjęcia i film” w kreatorze oferty ma trzy ścieżki: (A) wybierz film z połączonego konta TikTok, (B) wklej link do filmu, (C) nagraj w Rynku i opublikuj na TikTok (wymaga audytu).

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Jedyny serwis, który zna `client_key`, `client_secret` i tokeny TikToka. Pozostałe serwisy pytają go tylko o gotowe dane filmu (`videoId`, okładka, `embedUrl`).

### 7.1 Endpointy Rynku

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| GET | `/api/v1/tiktok/connect/url` | Zwraca adres autoryzacji TikTok z parametrem `state` (CSRF) | seller |
| GET | `/api/v1/tiktok/callback` | `redirect_uri` zarejestrowany w TikTok: wymiana `code` na tokeny, zapis zaszyfrowany | pub (z `state`) |
| GET | `/api/v1/tiktok/connection` | Status połączenia (połączone, wygasa, zakres uprawnień) | seller |
| DELETE | `/api/v1/tiktok/connection` | Rozłączenie konta + unieważnienie tokenu | seller |
| POST | `/api/v1/tiktok/sync` | Ręczna synchronizacja filmów | seller |
| GET | `/api/v1/tiktok/me/videos?cursor=` | Filmy z połączonego konta do wyboru w kreatorze oferty | seller |
| POST | `/api/v1/tiktok/me/videos/{externalVideoId}/attach` | Przypisanie filmu do oferty (`offerId`) | seller |
| POST | `/api/v1/videos/by-link` | Dodanie filmu po linku (oEmbed), bez połączenia konta | seller |
| GET | `/api/v1/videos/{videoId}` | Dane odtwarzania: `embedUrl`, `coverUrl` (świeża), czas trwania | pub |
| GET | `/api/v1/offers/{offerId}/video` | Film przypisany do oferty | pub |
| GET | `/api/v1/channels/{channelId}/videos?cursor=` | Zakładka „Filmy” kanału | pub |
| GET | `/api/v1/videos/shorts?cursor=` | Pionowe filmy do strumienia Shorts (źródło dla feedu) | pub |
| POST | `/api/v1/tiktok/posts` | Publikacja filmu z Rynku na TikTok: init posta, zwraca `publishId` i dane wysyłki | seller |
| GET | `/api/v1/tiktok/posts/{publishId}/status` | Status publikacji | seller |
| POST | `/api/v1/tiktok/webhooks` | Odbiornik zdarzeń TikTok (publikacja zakończona/nieudana, film publiczny) | pub (podpis) |
| GET | `/internal/videos?offerIds=` | Wsadowo: filmy i okładki dla kart ofert | int |

Zadania w tle (scheduler): odświeżanie access tokenów przed wygaśnięciem, odświeżanie okładek przez `video/query`, synchronizacja filmów co ok. 12 h, wykrywanie usuniętych/prywatnych filmów i oznaczanie ofert.

### 7.2 Wywołania do TikTok API (wychodzące)

Host: `https://open.tiktokapis.com`, chyba że podano inaczej.

| Cel | Metoda i adres | Scope |
|---|---|---|
| Autoryzacja (przeglądarka) | `GET https://www.tiktok.com/v2/auth/authorize/` (`client_key`, `scope`, `response_type=code`, `redirect_uri`, `state`) | — |
| Wymiana kodu na tokeny | `POST /v2/oauth/token/` (`grant_type=authorization_code`) | — |
| Odświeżenie tokenu | `POST /v2/oauth/token/` (`grant_type=refresh_token`) | — |
| Unieważnienie | `POST /v2/oauth/revoke/` | — |
| Dane konta | `GET /v2/user/info/?fields=open_id,union_id,avatar_url,display_name` | `user.info.basic` |
| Lista filmów | `POST /v2/video/list/` (`max_count` do 20, `cursor`) | `video.list` |
| Szczegóły / odświeżenie okładek | `POST /v2/video/query/` (`filters.video_ids`, do 20) | `video.list` |
| Informacje o twórcy przed postem | `POST /v2/post/publish/creator_info/query/` | — |
| Publikacja bezpośrednia | `POST /v2/post/publish/video/init/` | `video.publish` |
| Wysłanie do skrzynki użytkownika | `POST /v2/post/publish/inbox/video/init/` | `video.upload` |
| Wysłanie pliku | `PUT <upload_url>` (ważny godzinę) | — |
| Status publikacji | `POST /v2/post/publish/status/fetch/` | `video.upload` / `video.publish` |
| Osadzenie po linku | `GET https://www.tiktok.com/oembed?url=` | — |

Pola filmu przydatne dla Rynku: `id`, `title`, `video_description`, `duration`, `width`, `height`, `cover_image_url`, `embed_link`, `embed_html`, `share_url`, `view_count`, `like_count`, `comment_count`.

Limity z dokumentacji: init publikacji 6 żądań/min na token, status 30/min. Bez audytu posty tylko prywatne.

## Zdarzenia Kafka

Wiersze z §17, w których występuje `tiktok-integration` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `video.synced`, `video.attached`, `video.removed` | tiktok-integration | catalog, feed, search |

## Dane i zależności

- **Magazyn danych:** PostgreSQL (tokeny szyfrowane)
- **Zależności:** TikTok API (open.tiktokapis.com, oEmbed), catalog-service
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 4. `tiktok-integration-service` — połączenie konta, lista filmów, osadzanie; **najpierw ścieżka z linkiem (oEmbed) i z `video.list`**, publikowanie na TikTok dopiero po decyzji o audycie.

## Wyjątki routingu

Gateway (§2) kieruje ruch po prefiksie ścieżki, a poniższe endpointy leżą pod prefiksem innego serwisu. Gateway obsługuje je trasami szczegółowymi (`order: -1`), które mają pierwszeństwo przed trasami po prefiksie. Ścieżki pozostają bez zmian (decyzja z 2026-10-24). Test `RoutingTests` w `rynek-api-gateway` sprawdza, czy każda z nich trafia do właściwego serwisu.

- `/api/v1/offers/{offerId}/video`: należy do `tiktok-integration-service` (prefiks wskazuje `catalog-service`).
- `/api/v1/channels/{channelId}/videos`: należy do `tiktok-integration-service` (prefiks wskazuje `channel-service`).
