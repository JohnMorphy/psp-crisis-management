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

1. [Schemat SQL — pełne DDL](#1-schemat-sql--pełne-ddl)
2. [seed_dps.sql — placówki DPS](#2-seed_dpssql--placówki-dps)
3. [seed_relokacja.sql — miejsca relokacji](#3-seed_relokacjasql--miejsca-relokacji)
4. [seed_transport.sql — zasoby transportowe](#4-seed_transportsql--zasoby-transportowe)
5. [seed_layers.sql — konfiguracja warstw GIS](#5-seed_layerssql--konfiguracja-warstw-gis)
6. [seed_strefy.sql — strefy zagrożeń (demo)](#6-seed_strefysql--strefy-zagrożeń-demo)
7. [Pliki GeoJSON — granice administracyjne (legacy L-00)](#7-pliki-geojson--granice-administracyjne-legacy-l-00)
8. [Konfiguracja IKE — ike.config.json](#8-konfiguracja-ike--ikeconfigjson)
9. [Tabela granice_administracyjne — PRG WFS](#9-tabela-granice_administracyjne--prg-wfs)

---

## 1. Schemat SQL — pełne DDL

**Lokalizacja:** `backend/src/main/resources/db/schema.sql`

```sql
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
```

---

## 2. `seed_dps.sql` — placówki DPS

**Lokalizacja:** `backend/src/main/resources/db/seed_dps.sql`
**Tabela docelowa:** `placowka`
**Liczba rekordów:** 48 (po 2 na każdy z 24 powiatów)

> `ST_MakePoint(lon, lat)` — kolejność: **długość, szerokość** (X, Y).
> Współrzędne woj. lubelskiego: szer. 50.20–51.90°N, dług. 21.60–24.20°E.

```sql
INSERT INTO placowka
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, liczba_podopiecznych, niesamodzielni_procent,
     generator_backup, personel_dyzurny, kontakt, zrodlo)
VALUES
-- POWIAT LUBELSKI
('DPS-LBL-001', 'Dom Pomocy Społecznej im. Jana Pawła II w Niemcach',
 'DPS_dorosli', 'lubelski', 'Niemce', 'ul. Różana 14, 21-025 Niemce',
 ST_SetSRID(ST_MakePoint(22.5891, 51.3012), 4326),
 80, 72, 0.68, TRUE, 8, '81-756-32-10', 'syntetyczne'),

('DPS-LBL-002', 'Dom Pomocy Społecznej w Albertowie',
 'DPS_dorosli', 'lubelski', 'Bełżyce', 'Albertów 12, 24-200 Bełżyce',
 ST_SetSRID(ST_MakePoint(22.2841, 51.1723), 4326),
 100, 94, 0.82, FALSE, 10, '81-517-22-34', 'syntetyczne'),

-- POWIAT ZAMOJSKI
('DPS-ZAM-001', 'Dom Pomocy Społecznej w Zamościu',
 'DPS_dorosli', 'zamojski', 'Zamość', 'ul. Poprzeczna 6, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2517, 50.7230), 4326),
 120, 108, 0.74, TRUE, 14, '84-639-21-80', 'syntetyczne'),

('DPS-ZAM-002', 'Dom Pomocy Społecznej w Bodaczowie',
 'DPS_dzieci', 'zamojski', 'Szczebrzeszyn', 'Bodaczów 3, 22-460 Szczebrzeszyn',
 ST_SetSRID(ST_MakePoint(22.9822, 50.6961), 4326),
 45, 38, 0.45, FALSE, 5, '84-682-11-20', 'syntetyczne'),

-- POWIAT CHEŁM
('DPS-CHE-001', 'Dom Pomocy Społecznej przy ul. Polnej w Chełmie',
 'DPS_dorosli', 'chelm', 'Chełm', 'ul. Polna 15, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4720, 51.1433), 4326),
 90, 83, 0.71, TRUE, 9, '82-565-13-40', 'syntetyczne'),

('DPS-CHE-002', 'Dom Pomocy Społecznej w Sawinie',
 'DPS_dorosli', 'chelm', 'Sawin', 'ul. Chełmska 2, 22-107 Sawin',
 ST_SetSRID(ST_MakePoint(23.3984, 51.2108), 4326),
 60, 55, 0.89, FALSE, 6, '82-568-60-17', 'syntetyczne'),

-- POWIAT BIALSKI
('DPS-BIA-001', 'Dom Pomocy Społecznej w Białej Podlaskiej',
 'DPS_dorosli', 'bialski', 'Biała Podlaska', 'ul. Warszawska 18, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1167, 52.0333), 4326),
 110, 99, 0.65, TRUE, 12, '83-344-51-82', 'syntetyczne'),

('DPS-BIA-002', 'Dom Opieki Podlasie w Terespolu',
 'hostel_wspomagany', 'bialski', 'Terespol', 'ul. Kościuszki 7, 21-550 Terespol',
 ST_SetSRID(ST_MakePoint(23.6140, 52.0793), 4326),
 30, 27, 0.50, FALSE, 3, '83-375-20-15', 'syntetyczne'),

-- POWIAT PUŁAWSKI
('DPS-PUL-001', 'Dom Pomocy Społecznej w Puławach',
 'DPS_dorosli', 'pulaski', 'Puławy', 'ul. Piaskowa 21, 24-100 Puławy',
 ST_SetSRID(ST_MakePoint(21.9693, 51.4158), 4326),
 95, 88, 0.77, TRUE, 11, '81-886-32-50', 'syntetyczne'),

('DPS-PUL-002', 'Dom Pomocy Społecznej w Końskowoli',
 'DPS_dorosli', 'pulaski', 'Końskowola', 'ul. Ogrodowa 4, 24-130 Końskowola',
 ST_SetSRID(ST_MakePoint(22.0732, 51.3481), 4326),
 50, 47, 0.91, FALSE, 5, '81-881-64-15', 'syntetyczne'),

-- POWIAT HRUBIESZOWSKI
('DPS-HRU-001', 'Dom Pomocy Społecznej w Hrubieszowie',
 'DPS_dorosli', 'hrubieszowski', 'Hrubieszów', 'ul. B. Prusa 8, 22-500 Hrubieszów',
 ST_SetSRID(ST_MakePoint(23.8911, 50.8099), 4326),
 75, 70, 0.73, TRUE, 8, '84-696-22-80', 'syntetyczne'),

('DPS-HRU-002', 'Dom Pomocy Społecznej w Uchaniach',
 'DPS_dorosli', 'hrubieszowski', 'Uchanie', 'Uchanie 45, 22-510 Uchanie',
 ST_SetSRID(ST_MakePoint(23.6892, 50.8420), 4326),
 40, 36, 0.64, FALSE, 4, '84-696-10-03', 'syntetyczne'),

-- POWIAT KRASNOSTAWSKI
('DPS-KRA-S-001', 'Dom Pomocy Społecznej w Krasnymstawie',
 'DPS_dorosli', 'krasnostawski', 'Krasnystaw', 'ul. Poniatowskiego 25, 22-300 Krasnystaw',
 ST_SetSRID(ST_MakePoint(23.1716, 50.9860), 4326),
 90, 84, 0.73, TRUE, 10, '82-576-20-40', 'syntetyczne'),

('DPS-KRA-S-002', 'Dom Pomocy Społecznej w Zakrzówku',
 'DPS_dorosli', 'krasnostawski', 'Zakrzówek', 'Zakrzówek Wieś 10, 23-213 Zakrzówek',
 ST_SetSRID(ST_MakePoint(22.9480, 50.9122), 4326),
 50, 44, 0.68, FALSE, 5, '82-576-44-18', 'syntetyczne'),

-- POWIAT KRAŚNICKI
('DPS-KRA-001', 'Dom Pomocy Społecznej w Kraśniku',
 'DPS_dorosli', 'krasnicki', 'Kraśnik', 'ul. Słoneczna 9, 23-200 Kraśnik',
 ST_SetSRID(ST_MakePoint(22.2200, 50.9228), 4326),
 85, 79, 0.69, TRUE, 9, '81-884-20-20', 'syntetyczne'),

('DPS-KRA-002', 'Dom Dziecka w Annopolu',
 'dom_dziecka', 'krasnicki', 'Annopol', 'ul. Rynek 11, 23-235 Annopol',
 ST_SetSRID(ST_MakePoint(21.8583, 50.8888), 4326),
 25, 21, 0.24, FALSE, 3, '81-861-31-02', 'syntetyczne'),

-- POWIAT BIŁGORAJSKI
('DPS-BIL-001', 'Dom Pomocy Społecznej w Biłgoraju',
 'DPS_dorosli', 'bilgorajski', 'Biłgoraj', 'ul. Nadrzeczna 10, 23-400 Biłgoraj',
 ST_SetSRID(ST_MakePoint(22.7227, 50.5411), 4326),
 100, 91, 0.78, TRUE, 11, '84-686-51-30', 'syntetyczne'),

('DPS-BIL-002', 'Dom Pomocy Społecznej w Tarnogrodzie',
 'DPS_dorosli', 'bilgorajski', 'Tarnogród', 'ul. Leśna 3, 23-420 Tarnogród',
 ST_SetSRID(ST_MakePoint(22.7374, 50.3615), 4326),
 65, 60, 0.62, FALSE, 7, '84-689-71-03', 'syntetyczne'),

-- POWIAT ŁĘCZYŃSKI
('DPS-LEC-001', 'Dom Pomocy Społecznej w Łęcznej',
 'DPS_dorosli', 'leczynski', 'Łęczna', 'ul. Krasnystawska 52, 21-010 Łęczna',
 ST_SetSRID(ST_MakePoint(22.8862, 51.3022), 4326),
 70, 65, 0.58, TRUE, 7, '81-752-29-11', 'syntetyczne'),

('DPS-LEC-002', 'Dom Pomocy Społecznej w Milejowie',
 'DPS_dorosli', 'leczynski', 'Milejów', 'ul. Wesoła 8, 21-020 Milejów',
 ST_SetSRID(ST_MakePoint(22.9374, 51.2581), 4326),
 45, 41, 0.71, FALSE, 5, '81-757-60-44', 'syntetyczne'),

-- POWIAT ŚWIDNICKI
('DPS-SWI-001', 'Dom Pomocy Społecznej w Świdniku',
 'centrum_opiekuncze', 'swidnicki', 'Świdnik', 'ul. Powstańców 7, 21-040 Świdnik',
 ST_SetSRID(ST_MakePoint(22.6950, 51.2231), 4326),
 55, 51, 0.55, FALSE, 6, '81-751-43-20', 'syntetyczne'),

('DPS-SWI-002', 'Dom Pomocy Społecznej w Piaskach',
 'DPS_dorosli', 'swidnicki', 'Piaski', 'ul. Lubelska 22, 21-050 Piaski',
 ST_SetSRID(ST_MakePoint(22.8391, 51.1480), 4326),
 40, 37, 0.60, TRUE, 4, '81-757-51-30', 'syntetyczne'),

-- POWIAT OPOLSKI
('DPS-OPO-001', 'Dom Pomocy Społecznej w Opolu Lubelskim',
 'DPS_dorosli', 'opolski', 'Opole Lubelskie', 'ul. Ogrodowa 5, 24-300 Opole Lubelskie',
 ST_SetSRID(ST_MakePoint(21.9648, 51.1482), 4326),
 90, 82, 0.76, TRUE, 10, '81-827-60-70', 'syntetyczne'),

('DPS-OPO-002', 'Dom Pomocy Społecznej w Poniatowej',
 'DPS_dorosli', 'opolski', 'Poniatowa', 'ul. Fabryczna 14, 24-320 Poniatowa',
 ST_SetSRID(ST_MakePoint(22.0643, 51.1817), 4326),
 55, 49, 0.67, FALSE, 6, '81-820-30-25', 'syntetyczne'),

-- POWIAT RYCKI
('DPS-RYK-001', 'Dom Pomocy Społecznej w Rykach',
 'DPS_dorosli', 'rycki', 'Ryki', 'ul. Warszawska 50, 08-500 Ryki',
 ST_SetSRID(ST_MakePoint(21.9321, 51.6228), 4326),
 75, 68, 0.60, FALSE, 8, '81-865-32-10', 'syntetyczne'),

('DPS-RYK-002', 'Dom Pomocy Społecznej w Dęblinie',
 'DPS_dorosli', 'rycki', 'Dęblin', 'ul. Różana 3, 08-530 Dęblin',
 ST_SetSRID(ST_MakePoint(21.8484, 51.5612), 4326),
 50, 45, 0.72, TRUE, 5, '81-883-20-18', 'syntetyczne'),

-- POWIAT ŁUKOWSKI
('DPS-LUK-001', 'Dom Pomocy Społecznej w Łukowie',
 'DPS_dorosli', 'lukowski', 'Łuków', 'ul. Browarna 14, 21-400 Łuków',
 ST_SetSRID(ST_MakePoint(22.3831, 51.9308), 4326),
 80, 76, 0.72, TRUE, 9, '25-798-42-60', 'syntetyczne'),

('DPS-LUK-002', 'Dom Dziecka w Stoczku Łukowskim',
 'dom_dziecka', 'lukowski', 'Stoczek Łukowski', 'ul. Szkolna 5, 21-450 Stoczek Łukowski',
 ST_SetSRID(ST_MakePoint(22.0811, 51.9640), 4326),
 20, 17, 0.20, FALSE, 2, '25-797-50-10', 'syntetyczne'),

-- POWIAT RADZYŃSKI
('DPS-RAD-001', 'Dom Pomocy Społecznej w Radzyniu Podlaskim',
 'DPS_dorosli', 'radzynski', 'Radzyń Podlaski', 'ul. Sitkowskiego 3, 21-300 Radzyń Podlaski',
 ST_SetSRID(ST_MakePoint(22.6212, 51.7842), 4326),
 85, 79, 0.66, TRUE, 9, '83-352-71-77', 'syntetyczne'),

('DPS-RAD-002', 'Dom Pomocy Społecznej w Kąkolewnicy',
 'DPS_dorosli', 'radzynski', 'Kąkolewnica', 'Kąkolewnica 80, 21-302 Kąkolewnica',
 ST_SetSRID(ST_MakePoint(22.5041, 51.8220), 4326),
 40, 35, 0.74, FALSE, 4, '83-352-80-02', 'syntetyczne'),

-- POWIAT PARCZEWSKI
('DPS-PAR-001', 'Dom Pomocy Społecznej w Parczewie',
 'DPS_dorosli', 'parczewski', 'Parczew', 'ul. Szpitalna 2, 21-200 Parczew',
 ST_SetSRID(ST_MakePoint(22.9016, 51.6398), 4326),
 55, 50, 0.70, TRUE, 6, '83-355-10-33', 'syntetyczne'),

('DPS-PAR-002', 'Dom Pomocy Społecznej w Jabłoniu',
 'DPS_dorosli', 'parczewski', 'Jabłoń', 'Jabłoń 120, 21-205 Jabłoń',
 ST_SetSRID(ST_MakePoint(22.7340, 51.6811), 4326),
 35, 31, 0.65, FALSE, 4, '83-355-20-15', 'syntetyczne'),

-- POWIAT WŁODAWSKI
('DPS-VLO-001', 'Dom Pomocy Społecznej we Włodawie',
 'DPS_dorosli', 'wlodawski', 'Włodawa', 'ul. Czerwonego Krzyża 6, 22-200 Włodawa',
 ST_SetSRID(ST_MakePoint(23.5411, 51.5512), 4326),
 70, 63, 0.80, FALSE, 7, '82-572-20-12', 'syntetyczne'),

('DPS-VLO-002', 'Dom Pomocy Społecznej w Hannie',
 'DPS_dorosli', 'wlodawski', 'Hanna', 'Hanna 55, 22-220 Hanna',
 ST_SetSRID(ST_MakePoint(23.6530, 51.6801), 4326),
 30, 28, 0.86, FALSE, 3, '82-572-61-05', 'syntetyczne'),

-- POWIAT JANOWSKI
('DPS-JAN-001', 'Dom Pomocy Społecznej w Janowie Lubelskim',
 'DPS_dorosli', 'janowski', 'Janów Lubelski', 'ul. Zamoyskiego 3, 23-300 Janów Lubelski',
 ST_SetSRID(ST_MakePoint(22.4107, 50.7082), 4326),
 60, 54, 0.67, TRUE, 6, '15-872-38-19', 'syntetyczne'),

('DPS-JAN-002', 'Dom Pomocy Społecznej w Modliborzycach',
 'DPS_dorosli', 'janowski', 'Modliborzyce', 'ul. Szkolna 4, 23-310 Modliborzyce',
 ST_SetSRID(ST_MakePoint(22.3290, 50.7572), 4326),
 35, 31, 0.57, FALSE, 4, '15-871-20-08', 'syntetyczne'),

-- POWIAT TOMASZOWSKI
('DPS-TOM-001', 'Dom Pomocy Społecznej w Tomaszowie Lubelskim',
 'DPS_dorosli', 'tomaszowski', 'Tomaszów Lubelski', 'ul. Lwowska 18, 22-600 Tomaszów Lubelski',
 ST_SetSRID(ST_MakePoint(23.4209, 50.4482), 4326),
 90, 85, 0.75, TRUE, 10, '84-664-21-30', 'syntetyczne'),

('DPS-TOM-002', 'Dom Pomocy Społecznej w Tyszowcach',
 'DPS_dorosli', 'tomaszowski', 'Tyszowce', 'ul. Kościelna 12, 22-630 Tyszowce',
 ST_SetSRID(ST_MakePoint(23.7140, 50.6008), 4326),
 45, 40, 0.80, FALSE, 5, '84-661-30-20', 'syntetyczne'),

-- MIASTO ZAMOŚĆ (prawa powiatu)
('DPS-ZAM-M-001', 'Dom Pomocy Społecznej Caritas w Zamościu',
 'DPS_dorosli', 'zamosc', 'Zamość', 'ul. Okrzei 5, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2520, 50.7162), 4326),
 130, 117, 0.79, TRUE, 15, '84-638-60-90', 'syntetyczne'),

('DPS-ZAM-M-002', 'Centrum Opieki Senioralnej w Zamościu',
 'centrum_opiekuncze', 'zamosc', 'Zamość', 'ul. Wyszyńskiego 9, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2610, 50.7280), 4326),
 60, 55, 0.62, FALSE, 7, '84-639-44-11', 'syntetyczne'),

-- MIASTO LUBLIN (prawa powiatu)
('DPS-LBL-M-001', 'Dom Pomocy Społecznej przy ul. Głębokiej w Lublinie',
 'DPS_dorosli', 'lublin', 'Lublin', 'ul. Głęboka 11, 20-612 Lublin',
 ST_SetSRID(ST_MakePoint(22.5231, 51.2465), 4326),
 150, 138, 0.84, TRUE, 18, '81-466-51-00', 'syntetyczne'),

('DPS-LBL-M-002', 'Dom Pomocy Społecznej przy ul. Sławinkowskiej w Lublinie',
 'DPS_dorosli', 'lublin', 'Lublin', 'ul. Sławinkowska 37, 20-810 Lublin',
 ST_SetSRID(ST_MakePoint(22.5080, 51.2860), 4326),
 120, 109, 0.76, TRUE, 14, '81-744-17-22', 'syntetyczne'),

-- MIASTO CHEŁM (prawa powiatu)
('DPS-CHE-M-001', 'Dom Pomocy Społecznej przy ul. Ceramicznej w Chełmie',
 'DPS_dorosli', 'chelm', 'Chełm', 'ul. Ceramiczna 1, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4580, 51.1320), 4326),
 100, 92, 0.77, TRUE, 11, '82-562-78-30', 'syntetyczne'),

('DPS-CHE-M-002', 'Centrum Opiekuńczo-Mieszkalne w Chełmie',
 'centrum_opiekuncze', 'chelm', 'Chełm', 'ul. Połaniecka 4, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4810, 51.1488), 4326),
 40, 36, 0.50, FALSE, 4, '82-563-10-55', 'syntetyczne'),

-- MIASTO BIAŁA PODLASKA (prawa powiatu)
('DPS-BIA-M-001', 'Dom Pomocy Społecznej przy ul. Terebelskiej w Białej Podlaskiej',
 'DPS_dorosli', 'biala_podlaska', 'Biała Podlaska', 'ul. Terebelska 57, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1290, 52.0410), 4326),
 90, 83, 0.71, TRUE, 10, '83-343-30-30', 'syntetyczne'),

('DPS-BIA-M-002', 'Centrum Opieki w Białej Podlaskiej',
 'centrum_opiekuncze', 'biala_podlaska', 'Biała Podlaska', 'ul. Młodości 12, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1350, 52.0370), 4326),
 45, 40, 0.58, FALSE, 5, '83-343-40-40', 'syntetyczne');
```

> ⚠️ Dane syntetyczne. Nazwy miejscowości prawdziwe. Adresy, telefony i współrzędne
> przybliżone — wymagają weryfikacji przed użyciem w środowisku produkcyjnym.

---

## 3. `seed_relokacja.sql` — miejsca relokacji

```sql
INSERT INTO miejsca_relokacji
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, pojemnosc_dostepna, przyjmuje_niesamodzielnych, kontakt, zrodlo)
VALUES
('REL-LBL-001', 'Hala Sportowa MOSiR Lublin',
 'hala_sportowa', 'lublin', 'Lublin', 'ul. Filaretów 44, 20-609 Lublin',
 ST_SetSRID(ST_MakePoint(22.5372, 51.2401), 4326),
 350, 350, TRUE, '81-466-25-00', 'syntetyczne'),

('REL-ZAM-001', 'Hala Widowiskowo-Sportowa w Zamościu',
 'hala_sportowa', 'zamosc', 'Zamość', 'ul. Królowej Jadwigi 8, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2480, 50.7190), 4326),
 400, 400, TRUE, '84-639-30-80', 'syntetyczne'),

('REL-CHE-001', 'Hala Sportowa MOSiR Chełm',
 'hala_sportowa', 'chelm', 'Chełm', 'ul. Sienkiewicza 27, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4690, 51.1380), 4326),
 300, 300, TRUE, '82-565-35-00', 'syntetyczne'),

('REL-BIA-001', 'Hala Sportowa w Białej Podlaskiej',
 'hala_sportowa', 'biala_podlaska', 'Biała Podlaska', 'ul. Kolejowa 14, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1220, 52.0290), 4326),
 250, 250, TRUE, '83-342-81-30', 'syntetyczne'),

('REL-PUL-001', 'Centrum Sportu i Rekreacji w Puławach',
 'hala_sportowa', 'pulaski', 'Puławy', 'ul. Lubelska 5, 24-100 Puławy',
 ST_SetSRID(ST_MakePoint(21.9720, 51.4210), 4326),
 200, 200, FALSE, '81-880-04-00', 'syntetyczne'),

('REL-HRU-001', 'Dom Kultury w Hrubieszowie',
 'centrum_kultury', 'hrubieszowski', 'Hrubieszów', 'ul. Ciesielczuka 7, 22-500 Hrubieszów',
 ST_SetSRID(ST_MakePoint(23.8870, 50.8120), 4326),
 180, 180, TRUE, '84-696-28-95', 'syntetyczne'),

('REL-BIL-001', 'Centrum Kultury w Biłgoraju',
 'centrum_kultury', 'bilgorajski', 'Biłgoraj', 'ul. Kościuszki 16, 23-400 Biłgoraj',
 ST_SetSRID(ST_MakePoint(22.7190, 50.5380), 4326),
 200, 200, FALSE, '84-686-61-40', 'syntetyczne'),

('REL-KRA-001', 'Szkoła Podstawowa w Kraśniku',
 'szkola', 'krasnicki', 'Kraśnik', 'ul. Urzędowska 54, 23-200 Kraśnik',
 ST_SetSRID(ST_MakePoint(22.2280, 50.9190), 4326),
 100, 100, FALSE, '81-825-26-20', 'syntetyczne');
```

---

## 4. `seed_transport.sql` — zasoby transportowe

```sql
INSERT INTO zasob_transportu
    (kod, typ, oznaczenie, operator, powiat, geom,
     pojemnosc_osob, przyjmuje_niesamodzielnych, dostepny, zrodlo)
VALUES
('TRP-001', 'bus_sanitarny', 'Bus San. LUB-1', 'Pogotowie Ratunkowe Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5684, 51.2502), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-002', 'bus_sanitarny', 'Bus San. LUB-2', 'Pogotowie Ratunkowe Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5410, 51.2610), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-003', 'bus_zwykly', 'Bus PKS LUB-5', 'PKS Lublin',
 'lubelski', ST_SetSRID(ST_MakePoint(22.5200, 51.2380), 4326),
 45, FALSE, TRUE, 'syntetyczne'),

('TRP-004', 'karetka', 'Karetka LUB-K1', 'SPZOZ Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5750, 51.2480), 4326),
 2, TRUE, TRUE, 'syntetyczne'),

('TRP-005', 'bus_sanitarny', 'Bus San. CHE-1', 'Pogotowie Ratunkowe Chełm',
 'zamosc', ST_SetSRID(ST_MakePoint(23.4700, 51.1400), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-006', 'bus_sanitarny', 'Bus San. CHE-2', 'Pogotowie Ratunkowe Chełm',
 'chelm', ST_SetSRID(ST_MakePoint(23.3990, 51.2090), 4326),
 8, TRUE, FALSE, 'syntetyczne'),

('TRP-007', 'bus_zwykly', 'Bus PKS ZAM-3', 'PKS Zamość',
 'zamojski', ST_SetSRID(ST_MakePoint(23.2500, 50.7200), 4326),
 45, FALSE, TRUE, 'syntetyczne'),

('TRP-008', 'pojazd_specjalny', 'Pojazd Spec. STR-1', 'Straż Pożarna Lublin',
 'lublin', ST_SetSRID(ST_MakePoint(22.5600, 51.2550), 4326),
 12, TRUE, TRUE, 'syntetyczne'),

('TRP-009', 'bus_sanitarny', 'Bus San. BIA-1', 'Pogotowie Biała Podlaska',
 'bialski', ST_SetSRID(ST_MakePoint(23.1150, 52.0320), 4326),
 8, TRUE, TRUE, 'syntetyczne'),

('TRP-010', 'bus_zwykly', 'Bus PKS PUL-2', 'PKS Puławy',
 'pulaski', ST_SetSRID(ST_MakePoint(21.9680, 51.4140), 4326),
 45, FALSE, TRUE, 'syntetyczne');
```

---

## 5. `seed_layers.sql` — konfiguracja warstw GIS

```sql
INSERT INTO layer_config
    (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona,
     endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis)
VALUES
('L-01', 'DPS i placówki opiekuńcze',
 'DPSLayer', 'Point', TRUE,
 '/api/layers/L-01', 900, '#3B82F6', 'building',
 'Lokalizacja placówek DPS i domów opieki'),

('L-02', 'Gęstość podopiecznych',
 'HeatmapLayer', 'Heatmap', FALSE,
 '/api/layers/L-02', 900, NULL, 'flame',
 'Heatmapa koncentracji podopiecznych wymagających ewakuacji'),

('L-03', 'Strefy zagrożenia',
 'ZagrozeniaLayer', 'Polygon', TRUE,
 '/api/layers/L-03', 60, '#EF4444', 'alert-triangle',
 'Aktywne strefy zagrożenia — aktualizowane przy każdym imporcie scenariusza'),

('L-04', 'Drogi ewakuacyjne',
 'DrogiLayer', 'LineString', FALSE,
 '/api/layers/L-04', 60, '#22C55E', 'route',
 'Drogi z atrybutem drożności'),

('L-05', 'Dostępność transportu',
 'TransportLayer', 'Point', FALSE,
 '/api/layers/L-05', 60, '#8B5CF6', 'truck',
 'Lokalizacja dostępnych pojazdów ewakuacyjnych'),

('L-06', 'Miejsca relokacji',
 'RelokacjaLayer', 'Point', FALSE,
 '/api/layers/L-06', 900, '#10B981', 'home',
 'Dostępne miejsca przyjęcia ewakuowanych'),

('L-07', 'Białe plamy transportowe',
 'BialePlamiLayer', 'Polygon', FALSE,
 '/api/layers/L-07', 3600, '#6B7280', 'map-off',
 'Obszary bez regularnego transportu publicznego'),

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

## 6. `seed_strefy.sql` — strefy zagrożeń (demo)

Dane demonstracyjne ładowane przy inicjalizacji bazy.
W runtime tabela jest nadpisywana przez `FloodImportAgent`.

```sql
INSERT INTO strefy_zagrozen
    (kod, typ_zagrozenia, poziom, scenariusz, obszar, nazwa, geom,
     szybkosc_wznoszenia_m_h, czas_do_zagrozenia_h, zrodlo)
VALUES
('DEMO-POWODZ-001', 'powodz', 'czerwony', 'Q100', 'chelm',
 'Strefa zalewowa rzeki Uherka — rejon Sawina',
 ST_SetSRID(ST_GeomFromText(
   'POLYGON((23.36 51.18, 23.44 51.18, 23.44 51.25, 23.36 51.25, 23.36 51.18))'
 ), 4326),
 0.25, 3.0, 'syntetyczne'),

('DEMO-POWODZ-002', 'powodz', 'czerwony', 'Q100', 'wlodawski',
 'Strefa zalewowa rzeki Bug — rejon Włodawy',
 ST_SetSRID(ST_GeomFromText(
   'POLYGON((23.51 51.52, 23.58 51.52, 23.58 51.60, 23.51 51.60, 23.51 51.52))'
 ), 4326),
 0.25, 4.0, 'syntetyczne'),

('DEMO-BLACKOUT-001', 'blackout', 'zolty', 'blackout_powiat', 'hrubieszowski',
 'Strefa awarii sieci energetycznej — powiat hrubieszowski',
 ST_SetSRID(ST_GeomFromText(
   'POLYGON((23.72 50.76, 23.98 50.76, 23.98 50.87, 23.72 50.87, 23.72 50.76))'
 ), 4326),
 NULL, NULL, 'syntetyczne');
```

---

## 7. Pliki GeoJSON — granice administracyjne (legacy L-00)

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
> serwowane z tabeli `granice_administracyjne` — patrz §9.

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

## 8. Konfiguracja IKE — `ike.config.json`

**Lokalizacja:** `backend/src/main/resources/ike.config.json`
**Wczytywany przez:** `IkeAgent.java` (`@PostConstruct`) przez `ClassPathResource`
**API:** `GET /api/ike/config` — frontend pobiera konfigurację przez ten endpoint, nie czyta pliku bezpośrednio

> Plik leży po stronie backendu, bo wczytuje go wyłącznie `IkeAgent.java`.
> Frontend nigdy nie sięga do systemu plików — wagi i progi pobiera przez `GET /api/ike/config`.

```json
{
  "wagi": {
    "zagrozenie": 0.35,
    "niesamodzielni": 0.25,
    "transport_brak": 0.20,
    "droznosc_brak": 0.15,
    "odleglosc_relokacji": 0.05
  },
  "progi": {
    "czerwony": 0.70,
    "zolty": 0.40
  },
  "promienie_km": {
    "transport_dostepny": 15,
    "miejsca_relokacji": 50
  },
  "wartosci_domyslne": {
    "brak_danych_drogi": 0.5,
    "brak_transportu_w_promieniu": 1.0,
    "brak_danych_zagrozenia": 0.0
  }
}
```

Pełny opis algorytmu i edge case'ów: `docs/IKE_ALGORITHM.md`.

---

## 9. Tabela `granice_administracyjne` — PRG WFS

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
