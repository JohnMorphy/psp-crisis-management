CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- TABELA: placowka
-- ============================================================
CREATE TABLE IF NOT EXISTS placowka (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(20)  UNIQUE NOT NULL,
    nazwa                    VARCHAR(255) NOT NULL,
    typ                      VARCHAR(30)  CHECK (typ IN (
                               'DPS_dorosli', 'DPS_dzieci', 'dom_dziecka',
                               'hostel_wspomagany', 'centrum_opiekuncze'
                             )),
    powiat                   VARCHAR(100) NOT NULL,
    gmina                    VARCHAR(100) NOT NULL,
    adres                    TEXT,
    geom                     GEOMETRY(Point, 4326) NOT NULL,
    pojemnosc_ogolna         INTEGER      CHECK (pojemnosc_ogolna > 0),
    liczba_podopiecznych     INTEGER      CHECK (liczba_podopiecznych >= 0),
    niesamodzielni_procent   DECIMAL(4,3) CHECK (niesamodzielni_procent BETWEEN 0 AND 1),
    generator_backup         BOOLEAN      DEFAULT FALSE,
    personel_dyzurny         INTEGER      CHECK (personel_dyzurny >= 0),
    kontakt                  VARCHAR(50),
    ostatnia_aktualizacja    TIMESTAMPTZ  DEFAULT NOW(),
    zrodlo                   VARCHAR(20)  DEFAULT 'syntetyczne'
                             CHECK (zrodlo IN ('syntetyczne', 'scraping', 'mpips'))
);
CREATE INDEX IF NOT EXISTS idx_placowka_geom   ON placowka USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_placowka_powiat ON placowka(powiat);
CREATE INDEX IF NOT EXISTS idx_placowka_typ    ON placowka(typ);

-- ============================================================
-- TABELA: strefy_zagrozen
-- Wypełniana przez FloodImportAgent. Usuwana i uzupełniana przy każdym imporcie.
-- ============================================================
CREATE TABLE IF NOT EXISTS strefy_zagrozen (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(30)  UNIQUE NOT NULL,
    typ_zagrozenia           VARCHAR(20)  NOT NULL
                             CHECK (typ_zagrozenia IN ('powodz', 'pozar', 'blackout')),
    poziom                   VARCHAR(10)  NOT NULL
                             CHECK (poziom IN ('czerwony', 'zolty', 'zielony')),
    scenariusz               VARCHAR(20),   -- 'Q10' | 'Q100' | 'Q500' | 'pozar_maly' itp.
    nazwa                    VARCHAR(255),
    obszar                   VARCHAR(100),  -- kod powiatu lub bbox z żądania importu
    geom                     GEOMETRY(Polygon, 4326) NOT NULL,
    szybkosc_wznoszenia_m_h  DECIMAL(5,2),
    czas_do_zagrozenia_h     DECIMAL(6,1),
    zrodlo                   VARCHAR(20)    DEFAULT 'syntetyczne'
                             CHECK (zrodlo IN ('syntetyczne', 'wfs')),
    correlation_id           VARCHAR(36),   -- UUID z ThreatUpdatedEvent
    ostatnia_aktualizacja    TIMESTAMPTZ    DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_strefy_geom       ON strefy_zagrozen USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_strefy_typ        ON strefy_zagrozen(typ_zagrozenia, poziom);
CREATE INDEX IF NOT EXISTS idx_strefy_scenariusz ON strefy_zagrozen(scenariusz);

-- ============================================================
-- TABELA: miejsca_relokacji
-- ============================================================
CREATE TABLE IF NOT EXISTS miejsca_relokacji (
    id                         SERIAL PRIMARY KEY,
    kod                        VARCHAR(20)  UNIQUE NOT NULL,
    nazwa                      VARCHAR(255) NOT NULL,
    typ                        VARCHAR(30)  CHECK (typ IN (
                                 'hala_sportowa', 'szkola', 'DPS_przyjmujacy',
                                 'centrum_kultury', 'koszary'
                               )),
    powiat                     VARCHAR(100) NOT NULL,
    gmina                      VARCHAR(100) NOT NULL,
    adres                      TEXT,
    geom                       GEOMETRY(Point, 4326) NOT NULL,
    pojemnosc_ogolna           INTEGER      CHECK (pojemnosc_ogolna > 0),
    pojemnosc_dostepna         INTEGER      CHECK (pojemnosc_dostepna >= 0),
    przyjmuje_niesamodzielnych BOOLEAN      DEFAULT FALSE,
    kontakt                    VARCHAR(50),
    ostatnia_aktualizacja      TIMESTAMPTZ  DEFAULT NOW(),
    zrodlo                     VARCHAR(20)  DEFAULT 'syntetyczne'
);
CREATE INDEX IF NOT EXISTS idx_relokacja_geom ON miejsca_relokacji USING GIST(geom);

-- ============================================================
-- TABELA: zasob_transportu
-- ============================================================
CREATE TABLE IF NOT EXISTS zasob_transportu (
    id                         SERIAL PRIMARY KEY,
    kod                        VARCHAR(20)  UNIQUE NOT NULL,
    typ                        VARCHAR(30)  CHECK (typ IN (
                                 'bus_sanitarny', 'bus_zwykly', 'karetka',
                                 'pojazd_specjalny', 'ambulans'
                               )),
    oznaczenie                 VARCHAR(100),
    operator                   VARCHAR(255),
    powiat                     VARCHAR(100),
    geom                       GEOMETRY(Point, 4326),
    pojemnosc_osob             INTEGER      CHECK (pojemnosc_osob > 0),
    przyjmuje_niesamodzielnych BOOLEAN      DEFAULT FALSE,
    dostepny                   BOOLEAN      DEFAULT TRUE,
    ostatnia_aktualizacja      TIMESTAMPTZ  DEFAULT NOW(),
    zrodlo                     VARCHAR(20)  DEFAULT 'syntetyczne'
);
CREATE INDEX IF NOT EXISTS idx_transport_geom     ON zasob_transportu USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_transport_dostepny ON zasob_transportu(dostepny);

-- ============================================================
-- TABELA: layer_config
-- Każda warstwa GIS = jeden rekord. Nowa warstwa = INSERT, zero zmian w kodzie.
-- ============================================================
CREATE TABLE IF NOT EXISTS layer_config (
    id                       VARCHAR(10)  PRIMARY KEY,
    nazwa                    VARCHAR(100) NOT NULL,
    komponent                VARCHAR(100) NOT NULL,
    typ_geometrii            VARCHAR(20),
    domyslnie_wlaczona       BOOLEAN      DEFAULT FALSE,
    endpoint                 VARCHAR(255),
    interval_odswiezania_s   INTEGER      DEFAULT 900,
    kolor_domyslny           VARCHAR(7),
    ikona                    VARCHAR(50),
    opis                     TEXT,
    aktywna                  BOOLEAN      DEFAULT TRUE
);

-- ============================================================
-- TABELA: drogi_ewakuacyjne
-- ============================================================
CREATE TABLE IF NOT EXISTS drogi_ewakuacyjne (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(30)  UNIQUE NOT NULL,
    nazwa                    VARCHAR(255),
    kategoria                VARCHAR(20)  CHECK (kategoria IN (
                               'autostrada', 'krajowa', 'wojewodzka', 'powiatowa', 'gminna'
                             )),
    droznosc                 VARCHAR(20)  NOT NULL DEFAULT 'przejezdna'
                             CHECK (droznosc IN ('przejezdna', 'utrudnienia', 'zablokowana')),
    predkosc_max_kmh         INTEGER,
    blokada_opis             TEXT,
    geom                     GEOMETRY(LineString, 4326) NOT NULL,
    ostatnia_weryfikacja     TIMESTAMPTZ  DEFAULT NOW(),
    zrodlo                   VARCHAR(20)  DEFAULT 'syntetyczne'
);
CREATE INDEX IF NOT EXISTS idx_drogi_geom     ON drogi_ewakuacyjne USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_drogi_droznosc ON drogi_ewakuacyjne(droznosc);

-- ============================================================
-- TABELA: biale_plamy
-- ============================================================
CREATE TABLE IF NOT EXISTS biale_plamy (
    id     SERIAL PRIMARY KEY,
    kod    VARCHAR(30)  UNIQUE NOT NULL,
    nazwa  VARCHAR(255),
    powiat VARCHAR(100),
    geom   GEOMETRY(Polygon, 4326) NOT NULL,
    opis   TEXT,
    zrodlo VARCHAR(20)  DEFAULT 'syntetyczne'
);
CREATE INDEX IF NOT EXISTS idx_biale_plamy_geom ON biale_plamy USING GIST(geom);

-- ============================================================
-- TABELA: ike_results
-- Wypełniana wyłącznie przez IkeAgent po ThreatUpdatedEvent.
-- ============================================================
CREATE TABLE IF NOT EXISTS ike_results (
    placowka_kod             VARCHAR(20)   NOT NULL REFERENCES placowka(kod) ON DELETE CASCADE,
    ike_score                DECIMAL(5,4)  CHECK (ike_score BETWEEN 0 AND 1),
    ike_kategoria            VARCHAR(10)   CHECK (ike_kategoria IN (
                               'czerwony', 'zolty', 'zielony', 'nieznany'
                             )),
    -- ── Składowe score'ów (każda [0.0–1.0], NULL = nie można było obliczyć) ──
    score_zagrozenia         DECIMAL(5,4)  CHECK (score_zagrozenia         BETWEEN 0 AND 1),
    score_niesamodzielnych   DECIMAL(5,4)  CHECK (score_niesamodzielnych   BETWEEN 0 AND 1),
    score_braku_transportu   DECIMAL(5,4)  CHECK (score_braku_transportu   BETWEEN 0 AND 1),
    score_braku_droznosci    DECIMAL(5,4)  CHECK (score_braku_droznosci    BETWEEN 0 AND 1),
    score_odleglosci_relokacji DECIMAL(5,4) CHECK (score_odleglosci_relokacji BETWEEN 0 AND 1),
    -- ── Ostrzeżenia z edge case'ów (lista kodów, np. ["niesamodzielni_procent_brak"]) ──
    data_warnings            JSONB         DEFAULT '[]'::jsonb,
    -- ── Cel relokacji i trasa ──
    cel_relokacji_kod        VARCHAR(20)   REFERENCES miejsca_relokacji(kod),
    trasa_relokacji_geom     GEOMETRY(LineString, 4326),
    czas_przejazdu_min       INTEGER       CHECK (czas_przejazdu_min >= 0),
    correlation_id           VARCHAR(36),  -- UUID z ThreatUpdatedEvent który wywołał obliczenie
    obliczone_o              TIMESTAMPTZ   DEFAULT NOW(),
    PRIMARY KEY (placowka_kod)
);
CREATE INDEX IF NOT EXISTS idx_ike_score           ON ike_results(ike_score DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_ike_correlation     ON ike_results(correlation_id);
CREATE INDEX IF NOT EXISTS idx_ike_zagrozenie      ON ike_results(score_zagrozenia DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_ike_kategoria       ON ike_results(ike_kategoria);

-- ============================================================
-- TABELA: evacuation_decisions
-- Wypełniana przez DecisionAgent po IkeRecalculatedEvent.
-- ============================================================
CREATE TABLE IF NOT EXISTS evacuation_decisions (
    id                   SERIAL PRIMARY KEY,
    placowka_kod         VARCHAR(20)   REFERENCES placowka(kod),
    ike_score            DECIMAL(5,4),
    rekomendacja         VARCHAR(30)   CHECK (rekomendacja IN (
                           'ewakuuj_natychmiast', 'przygotuj_ewakuacje', 'monitoruj'
                         )),
    cel_relokacji_kod    VARCHAR(20)   REFERENCES miejsca_relokacji(kod),
    uzasadnienie         TEXT,
    zatwierdzona         BOOLEAN       DEFAULT NULL,  -- NULL=oczekuje, TRUE=zatwierdzona, FALSE=odrzucona
    correlation_id       VARCHAR(36),                 -- UUID z ThreatUpdatedEvent
    wygenerowano_o       TIMESTAMPTZ   DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_decisions_placowka    ON evacuation_decisions(placowka_kod);
CREATE INDEX IF NOT EXISTS idx_decisions_correlation ON evacuation_decisions(correlation_id);
CREATE INDEX IF NOT EXISTS idx_decisions_rekomendacja ON evacuation_decisions(rekomendacja);

-- ============================================================
-- TABELA: granice_administracyjne
-- Wypełniana przez AdminBoundaryImportAgent z GUGiK PRG WFS.
-- Import idempotentny: DELETE poziom + INSERT przy każdym wywołaniu importu.
-- ============================================================
CREATE TABLE IF NOT EXISTS granice_administracyjne (
    id              SERIAL PRIMARY KEY,
    kod_teryt       VARCHAR(7)   UNIQUE NOT NULL,
    nazwa           VARCHAR(200) NOT NULL,
    poziom          VARCHAR(12)  NOT NULL
                    CHECK (poziom IN ('wojewodztwo', 'powiat', 'gmina')),
    kod_nadrzedny   VARCHAR(6),
    geom            GEOMETRY(MULTIPOLYGON, 4326) NOT NULL,
    zrodlo          VARCHAR(20)  DEFAULT 'prg_wfs',
    data_importu    TIMESTAMPTZ  DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_granice_poziom       ON granice_administracyjne(poziom);
CREATE INDEX IF NOT EXISTS idx_granice_geom         ON granice_administracyjne USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_granice_kod_teryt    ON granice_administracyjne(kod_teryt);
CREATE INDEX IF NOT EXISTS idx_granice_kod_nadrz    ON granice_administracyjne(kod_nadrzedny);

-- ============================================================
-- TABELA: entity_category
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_category (
    code                 VARCHAR(80) PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    act_ref              TEXT,
    icon                 VARCHAR(50),
    default_layer_group  VARCHAR(50),
    geometry_mode        VARCHAR(20),
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- TABELA: entity_source
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_source (
    code                 VARCHAR(50) PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    protocol             VARCHAR(30),
    official             BOOLEAN      DEFAULT FALSE,
    import_mode          VARCHAR(30),
    endpoint_or_homepage TEXT,
    license_note         TEXT,
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- TABELA: entity_import_batch
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_import_batch (
    id              BIGSERIAL PRIMARY KEY,
    source_code     VARCHAR(50) NOT NULL REFERENCES entity_source(code),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL,
    records_total   INTEGER      DEFAULT 0,
    records_new     INTEGER      DEFAULT 0,
    records_updated INTEGER      DEFAULT 0,
    records_skipped INTEGER      DEFAULT 0,
    error_log       TEXT
);

-- ============================================================
-- TABELA: entity_registry
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_registry (
    id                   BIGSERIAL PRIMARY KEY,
    source_code          VARCHAR(50) NOT NULL REFERENCES entity_source(code),
    source_record_id     VARCHAR(120) NOT NULL,
    category_code        VARCHAR(80) NOT NULL REFERENCES entity_category(code),
    name                 VARCHAR(255) NOT NULL,
    subtitle             VARCHAR(255),
    status               VARCHAR(50),
    owner_name           VARCHAR(255),
    address_raw          TEXT,
    teryt_woj            VARCHAR(2),
    teryt_pow            VARCHAR(4),
    teryt_gmina          VARCHAR(7),
    lat                  DOUBLE PRECISION,
    lon                  DOUBLE PRECISION,
    geom                 GEOMETRY(Point, 4326),
    coverage_geom        GEOMETRY(Geometry, 4326),
    contact_phone        VARCHAR(100),
    contact_email        VARCHAR(255),
    www                  VARCHAR(255),
    attributes_json      JSONB         DEFAULT '{}'::jsonb,
    source_url           TEXT,
    last_seen_at         TIMESTAMPTZ   DEFAULT NOW(),
    last_import_batch_id BIGINT REFERENCES entity_import_batch(id),
    source_priority      INTEGER       DEFAULT 100,
    match_confidence     DECIMAL(4, 3),
    created_at           TIMESTAMPTZ   DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   DEFAULT NOW(),
    UNIQUE (source_code, source_record_id)
);
CREATE INDEX IF NOT EXISTS idx_entity_registry_source        ON entity_registry(source_code);
CREATE INDEX IF NOT EXISTS idx_entity_registry_category      ON entity_registry(category_code);
CREATE INDEX IF NOT EXISTS idx_entity_registry_teryt_woj     ON entity_registry(teryt_woj);
CREATE INDEX IF NOT EXISTS idx_entity_registry_teryt_pow     ON entity_registry(teryt_pow);
CREATE INDEX IF NOT EXISTS idx_entity_registry_teryt_gmina   ON entity_registry(teryt_gmina);
CREATE INDEX IF NOT EXISTS idx_entity_registry_geom          ON entity_registry USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_entity_registry_coverage_geom ON entity_registry USING GIST(coverage_geom);

-- ============================================================
-- TABELA: entity_alias
-- ============================================================
CREATE TABLE IF NOT EXISTS entity_alias (
    id               BIGSERIAL PRIMARY KEY,
    entity_id        BIGINT NOT NULL REFERENCES entity_registry(id) ON DELETE CASCADE,
    alias_type       VARCHAR(50) NOT NULL,
    alias_value      VARCHAR(255) NOT NULL,
    match_confidence DECIMAL(4, 3),
    UNIQUE (entity_id, alias_type, alias_value)
);
