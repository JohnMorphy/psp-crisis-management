# DATA_SCHEMA.md — Schematy danych

> Czytaj przed tworzeniem lub modyfikacją danych, encji JPA lub serwisów.
> PostgreSQL jest jedynym źródłem danych runtime.
> Pliki seed służą wyłącznie do inicjalizacji bazy.

### Konwencja identyfikatorów administracyjnych

Pole `powiat` (i `gmina`) we **wszystkich tabelach operacyjnych** używa slugów:
małe litery ASCII, bez polskich znaków, bez spacji, bez prefiksów.

| Forma w bazie (slug) | Forma prezentacyjna (tylko UI) |
|---|---|
| `lubelski` | Powiat lubelski |
| `chelm` | Powiat chełmski |
| `lublin` | Miasto Lublin |
| `zamosc` | Miasto Zamość |
| `biala_podlaska` | Miasto Biała Podlaska |
| `chelmski` | Powiat chełmski (obszar wiejski) |

Reguła zamiany: `toLowerCase()` → usuń polskie znaki → zamień spacje i myślniki
na `_` → usuń prefiks `m.` / `miasto` / `powiat`.

Pola `nazwa` i `adres` przechowują formy prezentacyjne — są wyłącznie do wyświetlania.
API przyjmuje slugi w parametrach filtrów (`?powiat=chelm`). Joiny i WHERE zawsze
operują na slugach. **Nie używaj form `M. Lublin`, `M. Chełm` w kolumnach `powiat`.**

---

## Spis treści

Pliki seed wykonywane w kolejności: `01_schema.sql` → `03_seed_layers.sql` → `07_seed_resource_types.sql`

1. [Schemat SQL — pełne DDL](#1-schemat-sql--pełne-ddl)
2. [03_seed_layers.sql — konfiguracja warstw GIS](#2-03_seed_layerssql--konfiguracja-warstw-gis)
3. [Pliki GeoJSON — granice administracyjne (legacy L-00)](#3-pliki-geojson--granice-administracyjne-legacy-l-00)
4. [Tabela granice_administracyjne — PRG WFS](#4-tabela-granice_administracyjne--prg-wfs)
5. [Tabela entity_registry — ujednolicony rejestr podmiotów](#5-tabela-entity_registry--ujednolicony-rejestr-podmiotów)

---

## 1. Schemat SQL — pełne DDL

**Lokalizacja:** `backend/src/main/resources/db/01_schema.sql`

```sql
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
    -- Kody TERYT: WOJ=2 cyfry, POW=4 cyfry (2woj+2pow), GMI=7 cyfr (2woj+2pow+2gmi+1typ)
    nazwa           VARCHAR(200) NOT NULL,
    poziom          VARCHAR(12)  NOT NULL
                    CHECK (poziom IN ('wojewodztwo', 'powiat', 'gmina')),
    kod_nadrzedny   VARCHAR(6),
    -- NULL dla województw; kod powiatu (4 cyfry) dla gmin; kod woj. (2 cyfry) dla powiatów
    geom            GEOMETRY(MULTIPOLYGON, 4326) NOT NULL,
    zrodlo          VARCHAR(20)  DEFAULT 'prg_wfs',
    data_importu    TIMESTAMPTZ  DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_granice_poziom       ON granice_administracyjne(poziom);
CREATE INDEX IF NOT EXISTS idx_granice_geom         ON granice_administracyjne USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_granice_kod_teryt    ON granice_administracyjne(kod_teryt);
CREATE INDEX IF NOT EXISTS idx_granice_kod_nadrz    ON granice_administracyjne(kod_nadrzedny);

```

---

## 2. `03_seed_layers.sql` — konfiguracja warstw GIS

> Kolejność plików seed: `01_schema.sql` → `03_seed_layers.sql` → `07_seed_resource_types.sql`

```sql
INSERT INTO layer_config
    (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona,
     endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis)
VALUES
('L-01', 'Jednostki ochrony ludności',
 'EntityLayer', 'Point', TRUE,
 '/api/layers/L-01', 900, '#3B82F6', 'building',
 'Lokalizacja jednostek ochrony ludności (DPS, placówki opiekuńcze, domy dziecka, hospicja)'),

('L-02', 'Gęstość podopiecznych',
 'HeatmapLayer', 'Heatmap', FALSE,
 '/api/layers/L-02', 900, NULL, 'flame',
 'Heatmapa koncentracji podopiecznych wymagających ewakuacji'),

('L-03', 'Alerty zagrożeń',
 'ThreatAlertLayer', 'Point', TRUE,
 '/api/layers/L-03', 60, '#EF4444', 'alert-triangle',
 'Aktywne alerty zagrożeń (powódź, pożar, blackout) z tabeli threat_alert'),

('L-04', 'Zasoby jednostek',
 'ResourceLayer', 'Point', FALSE,
 '/api/layers/L-04', 300, '#22C55E', 'truck',
 'Dostępne zasoby operacyjne przypisane do jednostek ochrony ludności'),

('L-05', 'Zasięg zagrożenia',
 'ThreatRadiusLayer', 'Polygon', FALSE,
 '/api/layers/L-05', 60, '#F59E0B', 'radio',
 'Obszar zasięgu alertu zagrożenia (radius_km wokół punktu threat_alert)'),

('L-06', 'Jednostki w zasięgu',
 'AffectedEntitiesLayer', 'Point', FALSE,
 '/api/layers/L-06', 60, '#8B5CF6', 'building-2',
 'Jednostki ochrony ludności w zasięgu aktywnych alertów'),

('L-07', 'Pokrycie zasobów',
 'ResourceCoverageLayer', 'Polygon', FALSE,
 '/api/layers/L-07', 3600, '#6B7280', 'map-off',
 'Obszary bez dostępnych zasobów wymaganego typu'),

-- Granice administracyjne całej Polski — dane z GUGiK PRG WFS (zadanie 1.9–1.11)
-- Tabela granice_administracyjne wypełniana przez AdminBoundaryImportAgent.
-- L-09 i L-10 wymagają filtra ?kod_woj= lub ?bbox= przy pobieraniu danych.
('L-08', 'Granice województw',
 'AdminBoundaryLayer', 'MultiPolygon', TRUE,
 '/api/layers/L-08', 86400, '#6366F1', 'map',
 'Granice 16 województw Polski — dane PRG GUGiK'),

('L-09', 'Granice powiatów',
 'AdminBoundaryLayer', 'MultiPolygon', FALSE,
 '/api/layers/L-09', 86400, '#4B5563', 'map',
 'Granice ~380 powiatów Polski — dane PRG GUGiK'),

('L-10', 'Granice gmin',
 'AdminBoundaryLayer', 'MultiPolygon', FALSE,
 '/api/layers/L-10', 86400, '#374151', 'map',
 'Granice ~2477 gmin Polski — dane PRG GUGiK (wymagany filtr kod_woj lub bbox)');
```

> ⚠️ **L-00 (legacy):** warstwa administracyjna serwowana ze statycznego pliku GeoJSON
> (tylko woj. lubelskie). Zastąpiona przez L-08/L-09/L-10. Pozostaje aktywna do czasu
> zakończenia zadania 1.11 — po nim oznaczona `aktywna = FALSE`.

---

## 3. Pliki GeoJSON — granice administracyjne (legacy L-00)

> **Status:** L-00 jest aktywne do czasu ukończenia zadania 1.11.
> Po wdrożeniu L-08/L-09/L-10 (PRG WFS) zostanie oznaczone `aktywna = FALSE`.

**Lokalizacja:** `backend/src/main/resources/geojson/`
**Serwowane przez:** `GeoService.java` → `GET /api/layers/L-00` (granice)

> **Uwaga architektoniczna:** `L-00` to świadomy wyjątek poza mechanizmem
> config-driven layers (tabela `layer_config`). Granice administracyjne są danymi
> statycznymi serwowanymi z pliku GeoJSON, nie z bazy — nie podlegają odświeżaniu
> przez WebSocket i nie mają rekordu w `layer_config`. Frontend traktuje `L-00`
> jako osobne żądanie inicjalizacyjne, niezależne od listy `GET /api/layers`.
>
> Docelowe zastąpienie: `L-08` (województwa), `L-09` (powiaty), `L-10` (gminy)
> serwowane z tabeli `granice_administracyjne` — patrz §4.

### Pobranie z GADM 4.1

```bash
# Powiaty (ADM2)
curl -L "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_POL_2.json" \
  -o /tmp/gadm41_POL_2.json

jq '{type:.type,features:[.features[]|select(.properties.GID_1=="POL.6_1")]}' \
  /tmp/gadm41_POL_2.json \
  > backend/src/main/resources/geojson/lublin_powiaty.geojson

# Gminy (ADM3)
curl -L "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_POL_3.json" \
  -o /tmp/gadm41_POL_3.json

jq '{type:.type,features:[.features[]|select(.properties.GID_1=="POL.6_1")]}' \
  /tmp/gadm41_POL_3.json \
  > backend/src/main/resources/geojson/lublin_gminy.geojson
```

### Fallback dla v1.0

```bash
cat > backend/src/main/resources/geojson/lublin_powiaty.geojson << 'EOF'
{
  "type": "FeatureCollection",
  "features": [{
    "type": "Feature",
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[21.60,50.20],[24.20,50.20],[24.20,51.90],[21.60,51.90],[21.60,50.20]]]
    },
    "properties": { "nazwa": "Województwo Lubelskie", "GID_1": "POL.6_1" }
  }]
}
EOF
```

---

## 4. Tabela `granice_administracyjne` — PRG WFS

**Implementacja:** zadania 1.9 (import) + 1.10 (API) + 1.11 (frontend)

### Źródło danych — GUGiK PRG WFS

```
Serwis: https://mapy.geoportal.gov.pl/wss/service/PZGIK/PRG/WFS/AdministrativeDivision
Protokół: WFS 2.0.0
Format wyjściowy: GML (domyślny) — konwersja GML→JTS przez GeoTools
CRS źródłowy: EPSG:2180 (PUWG 1992)
CRS docelowy: EPSG:4326 (WGS 84) — transformacja w AdminBoundaryImportAgent

Warstwy:
  TypeName: ms:A02_Granice_Wojewodztw   → poziom = 'wojewodztwo'  (16 rekordów)
  TypeName: ms:A03_Granice_Powiatow     → poziom = 'powiat'       (~380 rekordów)
  TypeName: ms:A04_Granice_Gmin         → poziom = 'gmina'        (~2477 rekordów)

Przykładowe żądanie GetFeature:
  GET {URL}?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature
           &TYPENAMES=ms:A02_Granice_Wojewodztw&COUNT=100
```

> ⚠️ Typowe pole TERYT w PRG: `JPT_KOD_JE` (kod TERYT) i `JPT_NAZWA_` (nazwa).
> Pola mogą się różnić między wersjami serwisu — weryfikuj przy implementacji
> przez `GetCapabilities` / `DescribeFeatureType`.

### Kody TERYT — konwencja

| Poziom | Długość | Przykład | Znaczenie |
|---|---|---|---|
| Województwo | 2 | `06` | Lubelskie |
| Powiat | 4 | `0601` | m. Lublin |
| Gmina | 7 | `0601011` | Lublin (gm. miejska) |

`kod_nadrzedny`:
- Województwo → `NULL`
- Powiat → pierwsze 2 znaki `kod_teryt` (kod województwa)
- Gmina → pierwsze 4 znaki `kod_teryt` (kod powiatu)

### Wydajność i ograniczenia

| Poziom | Liczba features | Rozmiar GeoJSON (est.) | Uwagi |
|---|---|---|---|
| Województwo | 16 | ~2 MB | Brak ograniczeń |
| Powiat | ~380 | ~40 MB | OK bez filtra |
| Gmina | ~2477 | ~250 MB | **Wymagany filtr** `?kod_woj=` lub `?bbox=` |

Potencjalne usprawnienie (DT — nie implementuj teraz): dwie geometrie per rekord —
`geom` (pełna, do PostGIS ST_Intersects) + `geom_uproszczona`
(ST_Simplify tolerance=0.001°, ~100m) do serwowania frontendowi.
Szacunkowa redukcja rozmiaru GeoJSON dla gmin: ~60–70%.

---

## 5. Tabela `entity_registry` — ujednolicony rejestr podmiotów

**Implementacja:** zadania 1.x (EntityRegistryEntry, EntityCategory, EntitySource, EntityImportBatch)

Tabela `entity_registry` to ujednolicony rejestr wszystkich podmiotów/jednostek ochrony ludności
importowanych z różnych źródeł (mpips, BIP, WFS, scraping). Jest główną tabelą operacyjną podmiotów
w systemie — eksponowana przez REST API jako `/api/entities`.

### Kluczowe tabele systemu entity registry

| Tabela | Opis |
|---|---|
| `entity_registry` | Główna tabela — każdy rekord to podmiot z danego źródła |
| `entity_category` | Kategorie podmiotów (DPS_dorosli, dom_dziecka, hospicjum, …) |
| `entity_source` | Źródła danych (mpips, BIP_powiat, GUGiK_WFS, scraping_html, …) |
| `entity_alias` | Alternatywne nazwy/kody tego samego podmiotu (deduplicacja) |
| `entity_import_batch` | Log partii importu — kiedy, ile rekordów, błędy |

### Powiązane encje Java

```
model/EntityRegistryEntry.java     → tabela entity_registry
model/EntityCategory.java          → tabela entity_category
model/EntitySource.java            → tabela entity_source
model/EntityAlias.java             → tabela entity_alias
model/EntityImportBatch.java       → tabela entity_import_batch

service/EntityRegistryService.java → logika biznesowa
service/EntityImportService.java   → import/upsert rekordów
service/EntityBootstrapService.java → inicjalizacja danych startowych

controller/EntityController.java   → GET /api/entities, /api/entities/{id}
controller/ImportController.java   → POST /api/import/*
```

### Kluczowe pola `entity_registry`

| Pole | Typ | Opis |
|---|---|---|
| `source_code` | VARCHAR(50) | Kod źródła (z tabeli `entity_source`) |
| `source_record_id` | VARCHAR(120) | Identyfikator rekordu w źródle |
| `category_code` | VARCHAR(80) | Kategoria (z tabeli `entity_category`) |
| `name` | VARCHAR(255) | Nazwa podmiotu |
| `teryt_woj/pow/gmina` | VARCHAR | Kody TERYT lokalizacji |
| `geom` | GEOMETRY(Point,4326) | Lokalizacja |
| `coverage_geom` | GEOMETRY | Zasięg działania (opcjonalny) |
| `attributes_json` | jsonb | Dowolne atrybuty specyficzne dla kategorii |
| `match_confidence` | DECIMAL(4,3) | Pewność dopasowania przy deduplicacji (0–1) |
| `last_import_batch_id` | BIGINT | FK do `entity_import_batch` |

---

### Tabela: `resource_type`

Słownik typów zasobów jednostek ochrony ludności.

```sql
CREATE TABLE IF NOT EXISTS resource_type (
    code            VARCHAR(80) PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    category        VARCHAR(50),
    unit_of_measure VARCHAR(20) DEFAULT 'szt'
);
```

Seed: `backend/src/main/resources/db/07_seed_resource_types.sql`
(~50 mockowanych typów: woz_cysternowy, ponton_motorowy, agregat_pradotworczy, nosze_transportowe, …)

---

### Tabela: `entity_resources`

Zasoby przypisane do jednostek (relacja wiele-do-wielu z entity_registry).

```sql
CREATE TABLE IF NOT EXISTS entity_resources (
    id                  BIGSERIAL PRIMARY KEY,
    entity_id           BIGINT NOT NULL REFERENCES entity_registry(id) ON DELETE CASCADE,
    resource_type_code  VARCHAR(80) NOT NULL REFERENCES resource_type(code),
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    is_available        BOOLEAN DEFAULT TRUE,
    last_updated        TIMESTAMPTZ DEFAULT NOW(),
    source              VARCHAR(20) DEFAULT 'mock',
    UNIQUE (entity_id, resource_type_code)
);
CREATE INDEX IF NOT EXISTS idx_entity_resources_entity   ON entity_resources(entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_resources_type     ON entity_resources(resource_type_code);
CREATE INDEX IF NOT EXISTS idx_entity_resources_avail    ON entity_resources(resource_type_code, is_available);
```

Zapytanie operacyjne (jednostki z zasobem w zasięgu):
```sql
SELECT DISTINCT e.id, e.name, e.geom
FROM entity_resources er
JOIN entity_registry e ON e.id = er.entity_id
WHERE er.resource_type_code = :resourceTypeCode
  AND er.is_available = TRUE
  AND ST_DWithin(e.geom::geography, ST_MakePoint(:lon,:lat)::geography, :radiusMeters);
```

---

### Tabela: `threat_alert`

Alerty zagrożeń z rzeczywistych API (IMGW) i triggerów manualnych. Zastępuje legacy `strefy_zagrozen`.

```sql
CREATE TABLE IF NOT EXISTS threat_alert (
    id             BIGSERIAL PRIMARY KEY,
    source_api     VARCHAR(50) NOT NULL,
    external_id    VARCHAR(120),
    threat_type    VARCHAR(30) NOT NULL,
    level          VARCHAR(10) NOT NULL CHECK (level IN ('warning', 'alarm', 'emergency')),
    value          DECIMAL(10,3),
    threshold      DECIMAL(10,3),
    unit           VARCHAR(20),
    geom           GEOMETRY(Point, 4326),
    radius_km      DECIMAL(6,1),
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ,
    is_active      BOOLEAN DEFAULT TRUE,
    correlation_id VARCHAR(36),
    raw_data       JSONB DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_threat_alert_geom   ON threat_alert USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_threat_alert_active ON threat_alert(is_active, threat_type);
CREATE INDEX IF NOT EXISTS idx_threat_alert_ext    ON threat_alert(source_api, external_id);
```

Zastępuje `strefy_zagrozen` — dane z realnych API, nie syntetyczne poligony.
