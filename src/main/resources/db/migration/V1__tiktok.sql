-- Połączenie konta sprzedawcy z TikTokiem. Tokeny zaszyfrowane (AES-GCM) kluczem z konfiguracji.
CREATE TABLE tiktok_connections (
    user_id            uuid PRIMARY KEY,
    channel_id         uuid         NOT NULL,
    open_id            varchar(100) NOT NULL,
    display_name       varchar(100) NOT NULL,
    scopes             varchar(300) NOT NULL,
    access_token       text         NOT NULL,
    refresh_token      text         NOT NULL,
    access_expires_at  timestamptz  NOT NULL,
    refresh_expires_at timestamptz  NOT NULL,
    connected_at       timestamptz  NOT NULL,
    last_sync_at       timestamptz
);

-- Parametr state z adresu autoryzacji (ochrona przed CSRF): jednorazowy, ważny 10 minut.
CREATE TABLE tiktok_oauth_states (
    state      varchar(64) PRIMARY KEY,
    user_id    uuid        NOT NULL,
    channel_id uuid        NOT NULL,
    created_at timestamptz NOT NULL
);

-- Filmy: z połączonego konta (TIKTOK) albo dodane po linku (LINK). offer_id: film przypisany do oferty (jedna oferta, jeden film).
CREATE TABLE videos (
    id            uuid PRIMARY KEY,
    channel_id    uuid         NOT NULL,
    owner_user_id uuid         NOT NULL,
    source        varchar(10)  NOT NULL,
    external_id   varchar(100) NOT NULL,
    title         varchar(300) NOT NULL,
    duration_sec  int          NOT NULL,
    vertical      boolean      NOT NULL,
    cover_url     varchar(500),
    embed_url     varchar(500) NOT NULL,
    view_count    bigint       NOT NULL,
    captions      boolean      NOT NULL,
    status        varchar(10)  NOT NULL,
    offer_id      uuid,
    created_at    timestamptz  NOT NULL,
    UNIQUE (owner_user_id, external_id)
);

CREATE INDEX videos_channel_idx ON videos (channel_id, created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX videos_shorts_idx ON videos (created_at DESC) WHERE status = 'ACTIVE' AND vertical;
CREATE INDEX videos_offer_idx ON videos (offer_id) WHERE offer_id IS NOT NULL;

-- Publikacje z Rynku na TikTok (Content Posting API).
CREATE TABLE tiktok_posts (
    publish_id varchar(100) PRIMARY KEY,
    user_id    uuid         NOT NULL,
    offer_id   uuid         NOT NULL,
    title      varchar(300) NOT NULL,
    status     varchar(20)  NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL
);
