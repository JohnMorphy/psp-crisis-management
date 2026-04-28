CREATE EXTENSION IF NOT EXISTS postgis;

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

-- Mendix unit geo-cache: stores only geometry for spatial queries.
-- Full unit details are fetched from Mendix REST API on demand (proxied by MendixUnitsController).
CREATE TABLE IF NOT EXISTS mendix_unit_cache (
    mendix_id     VARCHAR(255) PRIMARY KEY,
    geom          GEOMETRY(Point, 4326) NOT NULL,
    category_code VARCHAR(100) NOT NULL,
    synced_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mendix_unit_cache_geom
    ON mendix_unit_cache USING GIST (geom);
