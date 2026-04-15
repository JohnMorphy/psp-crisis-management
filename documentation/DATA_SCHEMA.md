# DATA_SCHEMA.md — Schematy danych

> Dokument referencyjny dla agenta kodowania i developerów.
> Opisuje strukturę tabel PostgreSQL, schematy seed SQL i format plików GeoJSON.
> **Czytaj przed tworzeniem lub modyfikacją jakichkolwiek danych, encji JPA lub serwisów.**

---

## Zasada nadrzędna

**PostgreSQL jest jedynym źródłem danych runtime.**
Pliki w tym dokumencie (`seed_*.sql`, pliki GeoJSON) służą wyłącznie do inicjalizacji bazy
przy pierwszym uruchomieniu. Aplikacja w trakcie działania nie odczytuje plików z dysku —
wszystkie dane pobiera przez Spring Data JPA lub natywne zapytania PostGIS.

---

## Spis treści

1. [Schemat SQL — pełne DDL](#1-schemat-sql--pełne-ddl)
2. [seed_dps.sql — placówki DPS](#2-seed_dpssql--placówki-dps)
3. [seed_relokacja.sql — miejsca relokacji](#3-seed_relokacjasql--miejsca-relokacji)
4. [seed_transport.sql — zasoby transportowe](#4-seed_transportsql--zasoby-transportowe)
5. [seed_layers.sql — konfiguracja warstw GIS](#5-seed_layerssql--konfiguracja-warstw-gis)
6. [seed_strefy.sql — strefy zagrożeń](#6-seed_strefysql--strefy-zagrożeń)
7. [seed_social.sql — feed social media](#7-seed_socialsql--feed-social-media)
8. [Pliki GeoJSON — granice administracyjne](#8-pliki-geojson--granice-administracyjne)
9. [Konfiguracja IKE — ike.config.json](#9-konfiguracja-ike--ikeconfigjson)

---

## 1. Schemat SQL — pełne DDL

**Lokalizacja:** `backend/src/main/resources/db/schema.sql`
**Wykonywany przez:** Spring Boot przy starcie (`spring.sql.init.mode=always` w dev)
lub ręcznie przy pierwszym wdrożeniu.

```sql
-- Wymagane rozszerzenie PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- TABELA: placowka
-- Placówki DPS i domy opieki. Główna tabela modułu ewakuacji.
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
-- TABELA: miejsca_relokacji
-- Obiekty przyjmujące ewakuowanych (hale, szkoły, inne DPS-y).
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
-- Pojazdy ewakuacyjne: busy, karetki, pojazdy specjalne.
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
-- Konfiguracja warstw GIS. Każda warstwa = jeden rekord.
-- Dodanie nowej warstwy = INSERT tutaj, zero zmian w kodzie.
-- ============================================================
CREATE TABLE IF NOT EXISTS layer_config (
    id                       VARCHAR(10)  PRIMARY KEY,   -- 'L-01' .. 'L-07'
    nazwa                    VARCHAR(100) NOT NULL,
    komponent                VARCHAR(100) NOT NULL,       -- nazwa komponentu React
    typ_geometrii            VARCHAR(20),
    domyslnie_wlaczona       BOOLEAN      DEFAULT FALSE,
    endpoint                 VARCHAR(255),
    interval_odswiezania_s   INTEGER      DEFAULT 900,
    kolor_domyslny           VARCHAR(7),                  -- hex #RRGGBB
    ikona                    VARCHAR(50),
    opis                     TEXT,
    aktywna                  BOOLEAN      DEFAULT TRUE
);

-- ============================================================
-- TABELA: strefy_zagrozen
-- Poligony stref zagrożenia: powódź, pożar, blackout.
-- ============================================================
CREATE TABLE IF NOT EXISTS strefy_zagrozen (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(30)  UNIQUE NOT NULL,
    typ_zagrozenia           VARCHAR(20)  NOT NULL
                             CHECK (typ_zagrozenia IN ('powodz', 'pozar', 'blackout')),
    poziom                   VARCHAR(10)  NOT NULL
                             CHECK (poziom IN ('czerwony', 'zolty', 'zielony')),
    nazwa                    VARCHAR(255),
    geom                     GEOMETRY(Polygon, 4326) NOT NULL,
    szybkosc_wznoszenia_m_h  DECIMAL(5,2),   -- dla powodzi: m/h
    czas_do_zagrozenia_h     DECIMAL(6,1),   -- szacowany czas do objęcia strefy
    zrodlo                   VARCHAR(20)     DEFAULT 'syntetyczne',
    ostatnia_aktualizacja    TIMESTAMPTZ     DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_strefy_geom ON strefy_zagrozen USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_strefy_typ  ON strefy_zagrozen(typ_zagrozenia, poziom);

-- ============================================================
-- TABELA: drogi_ewakuacyjne
-- Sieć dróg z atrybutem drożności. Geometria LineString.
-- ============================================================
CREATE TABLE IF NOT EXISTS drogi_ewakuacyjne (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(30)  UNIQUE NOT NULL,
    nazwa                    VARCHAR(255),
    kategoria                VARCHAR(20)  CHECK (kategoria IN (
                               'autostrada', 'krajowa', 'wojewodzka',
                               'powiatowa', 'gminna'
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
-- Poligony obszarów bez transportu publicznego.
-- ============================================================
CREATE TABLE IF NOT EXISTS biale_plamy (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(30)  UNIQUE NOT NULL,
    nazwa                    VARCHAR(255),
    powiat                   VARCHAR(100),
    geom                     GEOMETRY(Polygon, 4326) NOT NULL,
    opis                     TEXT,
    zrodlo                   VARCHAR(20)  DEFAULT 'syntetyczne'
);
CREATE INDEX IF NOT EXISTS idx_biale_plamy_geom ON biale_plamy USING GIST(geom);

-- ============================================================
-- TABELA: social_media_feed
-- Posty z social mediów z geolokalizacją. Seed = dane demo.
-- ============================================================
CREATE TABLE IF NOT EXISTS social_media_feed (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(30)  UNIQUE NOT NULL,
    platforma                VARCHAR(20)  CHECK (platforma IN ('twitter', 'facebook', 'instagram')),
    uzytkownik               VARCHAR(100),
    tresc                    TEXT         NOT NULL,
    data_publikacji          TIMESTAMPTZ  NOT NULL,
    geom                     GEOMETRY(Point, 4326),
    toponimy                 TEXT[],      -- tablica nazw miejscowości wykrytych w treści
    slowa_kluczowe           TEXT[],      -- triggery kryzysowe wykryte w treści
    pewnosc_geolokalizacji   DECIMAL(3,2) CHECK (pewnosc_geolokalizacji BETWEEN 0 AND 1),
    zweryfikowany            BOOLEAN      DEFAULT FALSE,
    zrodlo                   VARCHAR(20)  DEFAULT 'syntetyczne'
);
CREATE INDEX IF NOT EXISTS idx_social_geom ON social_media_feed USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_social_data ON social_media_feed(data_publikacji DESC);

-- ============================================================
-- TABELA: ike_results
-- Wyniki algorytmu IKE. Wypełniana wyłącznie przez IkeService.
-- ============================================================
CREATE TABLE IF NOT EXISTS ike_results (
    placowka_kod             VARCHAR(20)   NOT NULL REFERENCES placowka(kod) ON DELETE CASCADE,
    ike_score                DECIMAL(5,4)  CHECK (ike_score BETWEEN 0 AND 1),
    ike_kategoria            VARCHAR(10)   CHECK (ike_kategoria IN ('czerwony', 'zolty', 'zielony', 'nieznany')),
    cel_relokacji_kod        VARCHAR(20)   REFERENCES miejsca_relokacji(kod),
    trasa_relokacji_geom     GEOMETRY(LineString, 4326),
    czas_przejazdu_min       INTEGER       CHECK (czas_przejazdu_min >= 0),
    obliczone_o              TIMESTAMPTZ   DEFAULT NOW(),
    PRIMARY KEY (placowka_kod)
);
CREATE INDEX IF NOT EXISTS idx_ike_score ON ike_results(ike_score DESC NULLS LAST);
```

---

## 2. `seed_dps.sql` — placówki DPS

**Lokalizacja:** `backend/src/main/resources/db/seed_dps.sql`
**Ładuje dane do:** tabela `placowka`
**Liczba rekordów:** 48 (po 2 na każdy z 24 powiatów województwa lubelskiego)

### Format INSERT

```sql
INSERT INTO placowka
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, liczba_podopiecznych, niesamodzielni_procent,
     generator_backup, personel_dyzurny, kontakt, zrodlo)
VALUES
    ('DPS-LBL-001',
     'Dom Pomocy Społecznej im. Jana Pawła II w Niemcach',
     'DPS_dorosli', 'lubelski', 'Niemce',
     'ul. Różana 14, 21-025 Niemce',
     ST_SetSRID(ST_MakePoint(22.5891, 51.3012), 4326),
     80, 72, 0.68, TRUE, 8, '81-756-32-10', 'syntetyczne');
```

> **Ważne:** `ST_MakePoint(lon, lat)` — kolejność to **długość, szerokość** (X, Y).
> Nie odwracaj kolejności. Współrzędne dla województwa lubelskiego:
> szerokość 50.20–51.90°N, długość 21.60–24.20°E.

### Pełny seed — 48 placówek

```sql
INSERT INTO placowka
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, liczba_podopiecznych, niesamodzielni_procent,
     generator_backup, personel_dyzurny, kontakt, zrodlo)
VALUES
-- POWIAT LUBELSKI (2)
('DPS-LBL-001', 'Dom Pomocy Społecznej im. Jana Pawła II w Niemcach',
 'DPS_dorosli', 'lubelski', 'Niemce', 'ul. Różana 14, 21-025 Niemce',
 ST_SetSRID(ST_MakePoint(22.5891, 51.3012), 4326),
 80, 72, 0.68, TRUE, 8, '81-756-32-10', 'syntetyczne'),

('DPS-LBL-002', 'Dom Pomocy Społecznej w Albertowie',
 'DPS_dorosli', 'lubelski', 'Bełżyce', 'Albertów 12, 24-200 Bełżyce',
 ST_SetSRID(ST_MakePoint(22.2841, 51.1723), 4326),
 100, 94, 0.82, FALSE, 10, '81-517-22-34', 'syntetyczne'),

-- POWIAT ZAMOJSKI (2)
('DPS-ZAM-001', 'Dom Pomocy Społecznej w Zamościu',
 'DPS_dorosli', 'zamojski', 'Zamość', 'ul. Poprzeczna 6, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2517, 50.7230), 4326),
 120, 108, 0.74, TRUE, 14, '84-639-21-80', 'syntetyczne'),

('DPS-ZAM-002', 'Dom Pomocy Społecznej w Bodaczowie',
 'DPS_dzieci', 'zamojski', 'Szczebrzeszyn', 'Bodaczów 3, 22-460 Szczebrzeszyn',
 ST_SetSRID(ST_MakePoint(22.9822, 50.6961), 4326),
 45, 38, 0.45, FALSE, 5, '84-682-11-20', 'syntetyczne'),

-- POWIAT CHEŁM (2)
('DPS-CHE-001', 'Dom Pomocy Społecznej przy ul. Polnej w Chełmie',
 'DPS_dorosli', 'chelm', 'Chełm', 'ul. Polna 15, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4720, 51.1433), 4326),
 90, 83, 0.71, TRUE, 9, '82-565-13-40', 'syntetyczne'),

('DPS-CHE-002', 'Dom Pomocy Społecznej w Sawinie',
 'DPS_dorosli', 'chelm', 'Sawin', 'ul. Chełmska 2, 22-107 Sawin',
 ST_SetSRID(ST_MakePoint(23.3984, 51.2108), 4326),
 60, 55, 0.89, FALSE, 6, '82-568-60-17', 'syntetyczne'),

-- POWIAT BIALSKI (2)
('DPS-BIA-001', 'Dom Pomocy Społecznej w Białej Podlaskiej',
 'DPS_dorosli', 'bialski', 'Biała Podlaska', 'ul. Warszawska 18, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1167, 52.0333), 4326),
 110, 99, 0.65, TRUE, 12, '83-344-51-82', 'syntetyczne'),

('DPS-BIA-002', 'Dom Opieki Podlasie w Terespolu',
 'hostel_wspomagany', 'bialski', 'Terespol', 'ul. Kościuszki 7, 21-550 Terespol',
 ST_SetSRID(ST_MakePoint(23.6140, 52.0793), 4326),
 30, 27, 0.50, FALSE, 3, '83-375-20-15', 'syntetyczne'),

-- POWIAT PUŁAWSKI (2)
('DPS-PUL-001', 'Dom Pomocy Społecznej w Puławach',
 'DPS_dorosli', 'pulaski', 'Puławy', 'ul. Piaskowa 21, 24-100 Puławy',
 ST_SetSRID(ST_MakePoint(21.9693, 51.4158), 4326),
 95, 88, 0.77, TRUE, 11, '81-886-32-50', 'syntetyczne'),

('DPS-PUL-002', 'Dom Pomocy Społecznej w Końskowoli',
 'DPS_dorosli', 'pulaski', 'Końskowola', 'ul. Ogrodowa 4, 24-130 Końskowola',
 ST_SetSRID(ST_MakePoint(22.0732, 51.3481), 4326),
 50, 47, 0.91, FALSE, 5, '81-881-64-15', 'syntetyczne'),

-- POWIAT HRUBIESZOWSKI (2)
('DPS-HRU-001', 'Dom Pomocy Społecznej w Hrubieszowie',
 'DPS_dorosli', 'hrubieszowski', 'Hrubieszów', 'ul. B. Prusa 8, 22-500 Hrubieszów',
 ST_SetSRID(ST_MakePoint(23.8911, 50.8099), 4326),
 75, 70, 0.73, TRUE, 8, '84-696-22-80', 'syntetyczne'),

('DPS-HRU-002', 'Dom Pomocy Społecznej w Uchaniach',
 'DPS_dorosli', 'hrubieszowski', 'Uchanie', 'Uchanie 45, 22-510 Uchanie',
 ST_SetSRID(ST_MakePoint(23.6892, 50.8420), 4326),
 40, 36, 0.64, FALSE, 4, '84-696-10-03', 'syntetyczne'),

-- POWIAT KRASNOSTAWSKI (2)
('DPS-KRA-S-001', 'Dom Pomocy Społecznej w Krasnymstawie',
 'DPS_dorosli', 'krasnostawski', 'Krasnystaw', 'ul. Poniatowskiego 25, 22-300 Krasnystaw',
 ST_SetSRID(ST_MakePoint(23.1716, 50.9860), 4326),
 90, 84, 0.73, TRUE, 10, '82-576-20-40', 'syntetyczne'),

('DPS-KRA-S-002', 'Dom Pomocy Społecznej w Zakrzówku',
 'DPS_dorosli', 'krasnostawski', 'Zakrzówek', 'Zakrzówek Wieś 10, 23-213 Zakrzówek',
 ST_SetSRID(ST_MakePoint(22.9480, 50.9122), 4326),
 50, 44, 0.68, FALSE, 5, '82-576-44-18', 'syntetyczne'),

-- POWIAT KRAŚNICKI (2)
('DPS-KRA-001', 'Dom Pomocy Społecznej w Kraśniku',
 'DPS_dorosli', 'krasnicki', 'Kraśnik', 'ul. Słoneczna 9, 23-200 Kraśnik',
 ST_SetSRID(ST_MakePoint(22.2200, 50.9228), 4326),
 85, 79, 0.69, TRUE, 9, '81-884-20-20', 'syntetyczne'),

('DPS-KRA-002', 'Dom Dziecka w Annopolu',
 'dom_dziecka', 'krasnicki', 'Annopol', 'ul. Rynek 11, 23-235 Annopol',
 ST_SetSRID(ST_MakePoint(21.8583, 50.8888), 4326),
 25, 21, 0.24, FALSE, 3, '81-861-31-02', 'syntetyczne'),

-- POWIAT BIŁGORAJSKI (2)
('DPS-BIL-001', 'Dom Pomocy Społecznej w Biłgoraju',
 'DPS_dorosli', 'bilgorajski', 'Biłgoraj', 'ul. Nadrzeczna 10, 23-400 Biłgoraj',
 ST_SetSRID(ST_MakePoint(22.7227, 50.5411), 4326),
 100, 91, 0.78, TRUE, 11, '84-686-51-30', 'syntetyczne'),

('DPS-BIL-002', 'Dom Pomocy Społecznej w Tarnogrodzie',
 'DPS_dorosli', 'bilgorajski', 'Tarnogród', 'ul. Leśna 3, 23-420 Tarnogród',
 ST_SetSRID(ST_MakePoint(22.7374, 50.3615), 4326),
 65, 60, 0.62, FALSE, 7, '84-689-71-03', 'syntetyczne'),

-- POWIAT ŁĘCZYŃSKI (2)
('DPS-LEC-001', 'Dom Pomocy Społecznej w Łęcznej',
 'DPS_dorosli', 'leczynski', 'Łęczna', 'ul. Krasnystawska 52, 21-010 Łęczna',
 ST_SetSRID(ST_MakePoint(22.8862, 51.3022), 4326),
 70, 65, 0.58, TRUE, 7, '81-752-29-11', 'syntetyczne'),

('DPS-LEC-002', 'Dom Pomocy Społecznej w Milejowie',
 'DPS_dorosli', 'leczynski', 'Milejów', 'ul. Wesoła 8, 21-020 Milejów',
 ST_SetSRID(ST_MakePoint(22.9374, 51.2581), 4326),
 45, 41, 0.71, FALSE, 5, '81-757-60-44', 'syntetyczne'),

-- POWIAT ŚWIDNICKI (2)
('DPS-SWI-001', 'Dom Pomocy Społecznej w Świdniku',
 'centrum_opiekuncze', 'swidnicki', 'Świdnik', 'ul. Powstańców 7, 21-040 Świdnik',
 ST_SetSRID(ST_MakePoint(22.6950, 51.2231), 4326),
 55, 51, 0.55, FALSE, 6, '81-751-43-20', 'syntetyczne'),

('DPS-SWI-002', 'Dom Pomocy Społecznej w Piaskach',
 'DPS_dorosli', 'swidnicki', 'Piaski', 'ul. Lubelska 22, 21-050 Piaski',
 ST_SetSRID(ST_MakePoint(22.8391, 51.1480), 4326),
 40, 37, 0.60, TRUE, 4, '81-757-51-30', 'syntetyczne'),

-- POWIAT OPOLSKI (2)
('DPS-OPO-001', 'Dom Pomocy Społecznej w Opolu Lubelskim',
 'DPS_dorosli', 'opolski', 'Opole Lubelskie', 'ul. Ogrodowa 5, 24-300 Opole Lubelskie',
 ST_SetSRID(ST_MakePoint(21.9648, 51.1482), 4326),
 90, 82, 0.76, TRUE, 10, '81-827-60-70', 'syntetyczne'),

('DPS-OPO-002', 'Dom Pomocy Społecznej w Poniatowej',
 'DPS_dorosli', 'opolski', 'Poniatowa', 'ul. Fabryczna 14, 24-320 Poniatowa',
 ST_SetSRID(ST_MakePoint(22.0643, 51.1817), 4326),
 55, 49, 0.67, FALSE, 6, '81-820-30-25', 'syntetyczne'),

-- POWIAT RYCKI (2)
('DPS-RYK-001', 'Dom Pomocy Społecznej w Rykach',
 'DPS_dorosli', 'rycki', 'Ryki', 'ul. Warszawska 50, 08-500 Ryki',
 ST_SetSRID(ST_MakePoint(21.9321, 51.6228), 4326),
 75, 68, 0.60, FALSE, 8, '81-865-32-10', 'syntetyczne'),

('DPS-RYK-002', 'Dom Pomocy Społecznej w Dęblinie',
 'DPS_dorosli', 'rycki', 'Dęblin', 'ul. Różana 3, 08-530 Dęblin',
 ST_SetSRID(ST_MakePoint(21.8484, 51.5612), 4326),
 50, 45, 0.72, TRUE, 5, '81-883-20-18', 'syntetyczne'),

-- POWIAT ŁUKOWSKI (2)
('DPS-LUK-001', 'Dom Pomocy Społecznej w Łukowie',
 'DPS_dorosli', 'lukowski', 'Łuków', 'ul. Browarna 14, 21-400 Łuków',
 ST_SetSRID(ST_MakePoint(22.3831, 51.9308), 4326),
 80, 76, 0.72, TRUE, 9, '25-798-42-60', 'syntetyczne'),

('DPS-LUK-002', 'Dom Dziecka w Stoczku Łukowskim',
 'dom_dziecka', 'lukowski', 'Stoczek Łukowski', 'ul. Szkolna 5, 21-450 Stoczek Łukowski',
 ST_SetSRID(ST_MakePoint(22.0811, 51.9640), 4326),
 20, 17, 0.20, FALSE, 2, '25-797-50-10', 'syntetyczne'),

-- POWIAT RADZYŃSKI (2)
('DPS-RAD-001', 'Dom Pomocy Społecznej w Radzyniu Podlaskim',
 'DPS_dorosli', 'radzynski', 'Radzyń Podlaski', 'ul. Sitkowskiego 3, 21-300 Radzyń Podlaski',
 ST_SetSRID(ST_MakePoint(22.6212, 51.7842), 4326),
 85, 79, 0.66, TRUE, 9, '83-352-71-77', 'syntetyczne'),

('DPS-RAD-002', 'Dom Pomocy Społecznej w Kąkolewnicy',
 'DPS_dorosli', 'radzynski', 'Kąkolewnica', 'Kąkolewnica 80, 21-302 Kąkolewnica',
 ST_SetSRID(ST_MakePoint(22.5041, 51.8220), 4326),
 40, 35, 0.74, FALSE, 4, '83-352-80-02', 'syntetyczne'),

-- POWIAT PARCZEWSKI (2)
('DPS-PAR-001', 'Dom Pomocy Społecznej w Parczewie',
 'DPS_dorosli', 'parczewski', 'Parczew', 'ul. Szpitalna 2, 21-200 Parczew',
 ST_SetSRID(ST_MakePoint(22.9016, 51.6398), 4326),
 55, 50, 0.70, TRUE, 6, '83-355-10-33', 'syntetyczne'),

('DPS-PAR-002', 'Dom Pomocy Społecznej w Jabłoniu',
 'DPS_dorosli', 'parczewski', 'Jabłoń', 'Jabłoń 120, 21-205 Jabłoń',
 ST_SetSRID(ST_MakePoint(22.7340, 51.6811), 4326),
 35, 31, 0.65, FALSE, 4, '83-355-20-15', 'syntetyczne'),

-- POWIAT WŁODAWSKI (2)
('DPS-VLO-001', 'Dom Pomocy Społecznej we Włodawie',
 'DPS_dorosli', 'wlodawski', 'Włodawa', 'ul. Czerwonego Krzyża 6, 22-200 Włodawa',
 ST_SetSRID(ST_MakePoint(23.5411, 51.5512), 4326),
 70, 63, 0.80, FALSE, 7, '82-572-20-12', 'syntetyczne'),

('DPS-VLO-002', 'Dom Pomocy Społecznej w Hannie',
 'DPS_dorosli', 'wlodawski', 'Hanna', 'Hanna 55, 22-220 Hanna',
 ST_SetSRID(ST_MakePoint(23.6530, 51.6801), 4326),
 30, 28, 0.86, FALSE, 3, '82-572-61-05', 'syntetyczne'),

-- POWIAT JANOWSKI (2)
('DPS-JAN-001', 'Dom Pomocy Społecznej w Janowie Lubelskim',
 'DPS_dorosli', 'janowski', 'Janów Lubelski', 'ul. Zamoyskiego 3, 23-300 Janów Lubelski',
 ST_SetSRID(ST_MakePoint(22.4107, 50.7082), 4326),
 60, 54, 0.67, TRUE, 6, '15-872-38-19', 'syntetyczne'),

('DPS-JAN-002', 'Dom Pomocy Społecznej w Modliborzycach',
 'DPS_dorosli', 'janowski', 'Modliborzyce', 'ul. Szkolna 4, 23-310 Modliborzyce',
 ST_SetSRID(ST_MakePoint(22.3290, 50.7572), 4326),
 35, 31, 0.57, FALSE, 4, '15-871-20-08', 'syntetyczne'),

-- POWIAT TOMASZOWSKI (2)
('DPS-TOM-001', 'Dom Pomocy Społecznej w Tomaszowie Lubelskim',
 'DPS_dorosli', 'tomaszowski', 'Tomaszów Lubelski', 'ul. Lwowska 18, 22-600 Tomaszów Lubelski',
 ST_SetSRID(ST_MakePoint(23.4209, 50.4482), 4326),
 90, 85, 0.75, TRUE, 10, '84-664-21-30', 'syntetyczne'),

('DPS-TOM-002', 'Dom Pomocy Społecznej w Tyszowcach',
 'DPS_dorosli', 'tomaszowski', 'Tyszowce', 'ul. Kościelna 12, 22-630 Tyszowce',
 ST_SetSRID(ST_MakePoint(23.7140, 50.6008), 4326),
 45, 40, 0.80, FALSE, 5, '84-661-30-20', 'syntetyczne'),

-- MIASTO ZAMOŚĆ — prawa powiatu (2)
('DPS-ZAM-M-001', 'Dom Pomocy Społecznej Caritas w Zamościu',
 'DPS_dorosli', 'M. Zamość', 'Zamość', 'ul. Okrzei 5, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2520, 50.7162), 4326),
 130, 117, 0.79, TRUE, 15, '84-638-60-90', 'syntetyczne'),

('DPS-ZAM-M-002', 'Centrum Opieki Senioralnej w Zamościu',
 'centrum_opiekuncze', 'M. Zamość', 'Zamość', 'ul. Wyszyńskiego 9, 22-400 Zamość',
 ST_SetSRID(ST_MakePoint(23.2610, 50.7280), 4326),
 60, 55, 0.62, FALSE, 7, '84-639-44-11', 'syntetyczne'),

-- MIASTO LUBLIN — prawa powiatu (2)
('DPS-LBL-M-001', 'Dom Pomocy Społecznej przy ul. Głębokiej w Lublinie',
 'DPS_dorosli', 'M. Lublin', 'Lublin', 'ul. Głęboka 11, 20-612 Lublin',
 ST_SetSRID(ST_MakePoint(22.5231, 51.2465), 4326),
 150, 138, 0.84, TRUE, 18, '81-466-51-00', 'syntetyczne'),

('DPS-LBL-M-002', 'Dom Pomocy Społecznej przy ul. Sławinkowskiej w Lublinie',
 'DPS_dorosli', 'M. Lublin', 'Lublin', 'ul. Sławinkowska 37, 20-810 Lublin',
 ST_SetSRID(ST_MakePoint(22.5080, 51.2860), 4326),
 120, 109, 0.76, TRUE, 14, '81-744-17-22', 'syntetyczne'),

-- MIASTO CHEŁM — prawa powiatu (2)
('DPS-CHE-M-001', 'Dom Pomocy Społecznej przy ul. Ceramicznej w Chełmie',
 'DPS_dorosli', 'M. Chełm', 'Chełm', 'ul. Ceramiczna 1, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4580, 51.1320), 4326),
 100, 92, 0.77, TRUE, 11, '82-562-78-30', 'syntetyczne'),

('DPS-CHE-M-002', 'Centrum Opiekuńczo-Mieszkalne w Chełmie',
 'centrum_opiekuncze', 'M. Chełm', 'Chełm', 'ul. Połaniecka 4, 22-100 Chełm',
 ST_SetSRID(ST_MakePoint(23.4810, 51.1488), 4326),
 40, 36, 0.50, FALSE, 4, '82-563-10-55', 'syntetyczne'),

-- POWIAT ZAMBROWSKI (brak w woj. lubelskim — zastąpiony: POWIAT WŁODAWSKI dodatkowy)
-- POWIAT ŁĘCZYŃSKI dodatkowy (wyrównanie do 48 rekordów):
('DPS-BIA-M-001', 'Dom Pomocy Społecznej przy ul. Terebelskiej w Białej Podlaskiej',
 'DPS_dorosli', 'M. Biała Podlaska', 'Biała Podlaska', 'ul. Terebelska 57, 21-500 Biała Podlaska',
 ST_SetSRID(ST_MakePoint(23.1290, 52.0410), 4326),
 90, 83, 0.71, TRUE, 10, '83-343-30-30', 'syntetyczne'),

('DPS-RYK-003', 'Dom Dziecka w Żyrzynie',
 'dom_dziecka', 'pulaski', 'Żyrzyn', 'ul. Ogrodowa 2, 24-103 Żyrzyn',
 ST_SetSRID(ST_MakePoint(22.0920, 51.4980), 4326),
 18, 15, 0.22, FALSE, 2, '81-881-50-01', 'syntetyczne');
```

> ⚠️ Dane syntetyczne. Nazwy miejscowości i powiaty są prawdziwe.
> Adresy, numery telefonów i współrzędne są przybliżone — wymagają weryfikacji
> przed użyciem w środowisku produkcyjnym z prawdziwymi danymi.

---

## 3. `seed_relokacja.sql` — miejsca relokacji

**Lokalizacja:** `backend/src/main/resources/db/seed_relokacja.sql`
**Ładuje dane do:** tabela `miejsca_relokacji`

### Format INSERT

```sql
INSERT INTO miejsca_relokacji
    (kod, nazwa, typ, powiat, gmina, adres, geom,
     pojemnosc_ogolna, pojemnosc_dostepna,
     przyjmuje_niesamodzielnych, kontakt, zrodlo)
VALUES
    ('REL-LBL-001',
     'Hala Sportowa MOSiR Lublin',
     'hala_sportowa', 'M. Lublin', 'Lublin',
     'ul. Filaretów 44, 20-609 Lublin',
     ST_SetSRID(ST_MakePoint(22.5372, 51.2401), 4326),
     350, 350, TRUE, '81-466-25-00', 'syntetyczne'),

    ('REL-ZAM-001',
     'Hala Widowiskowo-Sportowa w Zamościu',
     'hala_sportowa', 'M. Zamość', 'Zamość',
     'ul. Królowej Jadwigi 8, 22-400 Zamość',
     ST_SetSRID(ST_MakePoint(23.2480, 50.7190), 4326),
     400, 400, TRUE, '84-639-30-80', 'syntetyczne'),

    ('REL-CHE-001',
     'Hala Sportowa MOSiR Chełm',
     'hala_sportowa', 'M. Chełm', 'Chełm',
     'ul. Henryka Sienkiewicza 27, 22-100 Chełm',
     ST_SetSRID(ST_MakePoint(23.4690, 51.1380), 4326),
     300, 300, TRUE, '82-565-35-00', 'syntetyczne'),

    ('REL-BIA-001',
     'Hala Sportowa w Białej Podlaskiej',
     'hala_sportowa', 'M. Biała Podlaska', 'Biała Podlaska',
     'ul. Kolejowa 14, 21-500 Biała Podlaska',
     ST_SetSRID(ST_MakePoint(23.1220, 52.0290), 4326),
     250, 250, TRUE, '83-342-81-30', 'syntetyczne'),

    ('REL-PUL-001',
     'Centrum Sportu i Rekreacji w Puławach',
     'hala_sportowa', 'pulaski', 'Puławy',
     'ul. Lubelska 5, 24-100 Puławy',
     ST_SetSRID(ST_MakePoint(21.9720, 51.4210), 4326),
     200, 200, FALSE, '81-880-04-00', 'syntetyczne'),

    ('REL-LBL-002',
     'Szkoła Podstawowa nr 28 w Lublinie',
     'szkola', 'M. Lublin', 'Lublin',
     'ul. Radości 13, 20-863 Lublin',
     ST_SetSRID(ST_MakePoint(22.5650, 51.2150), 4326),
     150, 150, FALSE, '81-744-23-40', 'syntetyczne'),

    ('REL-ZAM-002',
     'Liceum Ogólnokształcące im. Jana Zamoyskiego w Zamościu',
     'szkola', 'M. Zamość', 'Zamość',
     'ul. Wyszyńskiego 4, 22-400 Zamość',
     ST_SetSRID(ST_MakePoint(23.2540, 50.7240), 4326),
     120, 120, FALSE, '84-638-34-44', 'syntetyczne'),

    ('REL-HRU-001',
     'Dom Kultury w Hrubieszowie',
     'centrum_kultury', 'hrubieszowski', 'Hrubieszów',
     'ul. Ciesielczuka 7, 22-500 Hrubieszów',
     ST_SetSRID(ST_MakePoint(23.8870, 50.8120), 4326),
     180, 180, TRUE, '84-696-28-95', 'syntetyczne'),

    ('REL-BIL-001',
     'Centrum Kultury w Biłgoraju',
     'centrum_kultury', 'bilgorajski', 'Biłgoraj',
     'ul. Tadeusza Kościuszki 16, 23-400 Biłgoraj',
     ST_SetSRID(ST_MakePoint(22.7190, 50.5380), 4326),
     200, 200, FALSE, '84-686-61-40', 'syntetyczne'),

    ('REL-KRA-001',
     'Szkoła Podstawowa w Kraśniku',
     'szkola', 'krasnicki', 'Kraśnik',
     'ul. Urzędowska 54, 23-200 Kraśnik',
     ST_SetSRID(ST_MakePoint(22.2280, 50.9190), 4326),
     100, 100, FALSE, '81-825-26-20', 'syntetyczne');
```

---

## 4. `seed_transport.sql` — zasoby transportowe

**Lokalizacja:** `backend/src/main/resources/db/seed_transport.sql`
**Ładuje dane do:** tabela `zasob_transportu`

### Format INSERT

```sql
INSERT INTO zasob_transportu
    (kod, typ, oznaczenie, operator, powiat, geom,
     pojemnosc_osob, przyjmuje_niesamodzielnych, dostepny, zrodlo)
VALUES
    ('TRP-001', 'bus_sanitarny', 'Bus San. LUB-1',
     'Pogotowie Ratunkowe Lublin', 'M. Lublin',
     ST_SetSRID(ST_MakePoint(22.5684, 51.2502), 4326),
     8, TRUE, TRUE, 'syntetyczne'),

    ('TRP-002', 'bus_sanitarny', 'Bus San. LUB-2',
     'Pogotowie Ratunkowe Lublin', 'M. Lublin',
     ST_SetSRID(ST_MakePoint(22.5410, 51.2610), 4326),
     8, TRUE, TRUE, 'syntetyczne'),

    ('TRP-003', 'bus_zwykly', 'Bus PKS LUB-5',
     'PKS Lublin', 'lubelski',
     ST_SetSRID(ST_MakePoint(22.5200, 51.2380), 4326),
     45, FALSE, TRUE, 'syntetyczne'),

    ('TRP-004', 'karetka', 'Karetka LUB-K1',
     'SPZOZ Lublin', 'M. Lublin',
     ST_SetSRID(ST_MakePoint(22.5750, 51.2480), 4326),
     2, TRUE, TRUE, 'syntetyczne'),

    ('TRP-005', 'bus_sanitarny', 'Bus San. CHE-1',
     'Pogotowie Ratunkowe Chełm', 'M. Chełm',
     ST_SetSRID(ST_MakePoint(23.4700, 51.1400), 4326),
     8, TRUE, TRUE, 'syntetyczne'),

    ('TRP-006', 'bus_sanitarny', 'Bus San. CHE-2',
     'Pogotowie Ratunkowe Chełm', 'chelm',
     ST_SetSRID(ST_MakePoint(23.3990, 51.2090), 4326),
     8, TRUE, FALSE, 'syntetyczne'),

    ('TRP-007', 'bus_zwykly', 'Bus PKS ZAM-3',
     'PKS Zamość', 'zamojski',
     ST_SetSRID(ST_MakePoint(23.2500, 50.7200), 4326),
     45, FALSE, TRUE, 'syntetyczne'),

    ('TRP-008', 'pojazd_specjalny', 'Pojazd Spec. STR-1',
     'Straż Pożarna Lublin', 'M. Lublin',
     ST_SetSRID(ST_MakePoint(22.5600, 51.2550), 4326),
     12, TRUE, TRUE, 'syntetyczne'),

    ('TRP-009', 'bus_sanitarny', 'Bus San. BIA-1',
     'Pogotowie Biała Podlaska', 'bialski',
     ST_SetSRID(ST_MakePoint(23.1150, 52.0320), 4326),
     8, TRUE, TRUE, 'syntetyczne'),

    ('TRP-010', 'bus_zwykly', 'Bus PKS PUL-2',
     'PKS Puławy', 'pulaski',
     ST_SetSRID(ST_MakePoint(21.9680, 51.4140), 4326),
     45, FALSE, TRUE, 'syntetyczne');
```

---

## 5. `seed_layers.sql` — konfiguracja warstw GIS

**Lokalizacja:** `backend/src/main/resources/db/seed_layers.sql`
**Ładuje dane do:** tabela `layer_config`

```sql
INSERT INTO layer_config
    (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona,
     endpoint, interval_odswiezania_s, kolor_domyslny, ikona, opis)
VALUES
    ('L-01', 'DPS i placówki opiekuńcze',
     'DPSLayer', 'Point', TRUE,
     '/api/layers/L-01', 900, '#3B82F6', 'building',
     'Lokalizacja placówek DPS i domów opieki w województwie lubelskim'),

    ('L-02', 'Gęstość podopiecznych',
     'HeatmapLayer', 'Heatmap', FALSE,
     '/api/layers/L-02', 900, NULL, 'flame',
     'Heatmapa koncentracji podopiecznych wymagających ewakuacji'),

    ('L-03', 'Strefy zagrożenia',
     'ZagrozeniaLayer', 'Polygon', TRUE,
     '/api/layers/L-03', 300, '#EF4444', 'alert-triangle',
     'Strefy zagrożenia: powódź (niebieski), pożar (pomarańczowy), blackout (żółty)'),

    ('L-04', 'Drogi ewakuacyjne',
     'DrogiLayer', 'LineString', FALSE,
     '/api/layers/L-04', 60, '#22C55E', 'route',
     'Drogi z atrybutem drożności: zielona (przejezdna), czerwona (zablokowana)'),

    ('L-05', 'Dostępność transportu',
     'TransportLayer', 'Point', FALSE,
     '/api/layers/L-05', 60, '#8B5CF6', 'truck',
     'Lokalizacja dostępnych pojazdów ewakuacyjnych'),

    ('L-06', 'Miejsca relokacji',
     'RelokacjaLayer', 'Point', FALSE,
     '/api/layers/L-06', 900, '#10B981', 'home',
     'Dostępne miejsca przyjęcia ewakuowanych: hale, szkoły, inne DPS-y'),

    ('L-07', 'Białe plamy transportowe',
     'BialePlamiLayer', 'Polygon', FALSE,
     '/api/layers/L-07', 3600, '#6B7280', 'map-off',
     'Obszary bez regularnego transportu publicznego');
```

---

## 6. `seed_strefy.sql` — strefy zagrożeń

**Lokalizacja:** `backend/src/main/resources/db/seed_strefy.sql`
**Ładuje dane do:** tabela `strefy_zagrozen`

Dane syntetyczne — poligony wzdłuż rzeczywistych koryt rzek lubelskich
(Wieprz, Bug, Bystrzyca, Huczwa, Tanew).

```sql
INSERT INTO strefy_zagrozen
    (kod, typ_zagrozenia, poziom, nazwa, geom,
     szybkosc_wznoszenia_m_h, czas_do_zagrozenia_h, zrodlo)
VALUES
    ('POWODZ-WIE-001', 'powodz', 'czerwony',
     'Strefa zalewowa rzeki Wieprz — rejon Łęcznej',
     ST_SetSRID(ST_GeomFromText(
       'POLYGON((22.82 51.27, 22.92 51.27, 22.92 51.33, 22.82 51.33, 22.82 51.27))'
     ), 4326),
     0.30, 3.0, 'syntetyczne'),

    ('POWODZ-BUG-001', 'powodz', 'czerwony',
     'Strefa zalewowa rzeki Bug — rejon Włodawy',
     ST_SetSRID(ST_GeomFromText(
       'POLYGON((23.51 51.52, 23.58 51.52, 23.58 51.60, 23.51 51.60, 23.51 51.52))'
     ), 4326),
     0.25, 4.0, 'syntetyczne'),

    ('POWODZ-WIE-002', 'powodz', 'zolty',
     'Strefa zalewowa rzeki Wieprz — rejon Sawina',
     ST_SetSRID(ST_GeomFromText(
       'POLYGON((23.36 51.18, 23.44 51.18, 23.44 51.25, 23.36 51.25, 23.36 51.18))'
     ), 4326),
     0.20, 6.0, 'syntetyczne'),

    ('BLACKOUT-001', 'blackout', 'zolty',
     'Strefa awarii sieci energetycznej — rejon Hrubieszowa',
     ST_SetSRID(ST_GeomFromText(
       'POLYGON((23.82 50.77, 23.95 50.77, 23.95 50.85, 23.82 50.85, 23.82 50.77))'
     ), 4326),
     NULL, NULL, 'syntetyczne'),

    ('POZAR-001', 'pozar', 'zolty',
     'Strefa zagrożenia pożarowego — Las Janowski',
     ST_SetSRID(ST_GeomFromText(
       'POLYGON((22.35 50.65, 22.48 50.65, 22.48 50.74, 22.35 50.74, 22.35 50.65))'
     ), 4326),
     NULL, NULL, 'syntetyczne');
```

---

## 7. `seed_social.sql` — feed social media

**Lokalizacja:** `backend/src/main/resources/db/seed_social.sql`
**Ładuje dane do:** tabela `social_media_feed`

Dane demonstracyjne — posty z geolokalizacją w województwie lubelskim.

```sql
INSERT INTO social_media_feed
    (kod, platforma, uzytkownik, tresc, data_publikacji, geom,
     toponimy, slowa_kluczowe, pewnosc_geolokalizacji, zweryfikowany, zrodlo)
VALUES
    ('SM-001', 'twitter', '@mieszkaniec_lbl',
     'Woda już wchodzi do piwnic przy ul. Nadrzecznej w Annopolu! Kilka rodzin prosi o pomoc. #powodz',
     '2026-04-14T09:23:00Z',
     ST_SetSRID(ST_MakePoint(21.8570, 50.8901), 4326),
     ARRAY['Annopol', 'ul. Nadrzeczna'],
     ARRAY['woda', 'powodz', 'pomoc'],
     0.91, FALSE, 'syntetyczne'),

    ('SM-002', 'facebook', 'Jan Kowalski',
     'Droga przez Sawin całkowicie zalana, nie ma przejazdu w kierunku Chełma.',
     '2026-04-14T09:18:00Z',
     ST_SetSRID(ST_MakePoint(23.3984, 51.2108), 4326),
     ARRAY['Sawin', 'Chełm'],
     ARRAY['droga', 'zalana'],
     0.88, TRUE, 'syntetyczne'),

    ('SM-003', 'twitter', '@straż_lbl',
     'Uwaga! Przerwa w dostawie prądu w gminie Hrubieszów. Szacowany czas przywrócenia: 8h.',
     '2026-04-14T08:45:00Z',
     ST_SetSRID(ST_MakePoint(23.8911, 50.8099), 4326),
     ARRAY['Hrubieszów'],
     ARRAY['prad', 'blackout'],
     0.95, TRUE, 'syntetyczne'),

    ('SM-004', 'facebook', 'Maria Nowak',
     'DPS w Sawinie ewakuuje część pensjonariuszy, autokar już podstawiony.',
     '2026-04-14T09:35:00Z',
     ST_SetSRID(ST_MakePoint(23.3990, 51.2100), 4326),
     ARRAY['Sawin'],
     ARRAY['ewakuacja', 'DPS'],
     0.92, FALSE, 'syntetyczne'),

    ('SM-005', 'twitter', '@kierowca_pks',
     'Trasa Włodawa-Chełm nieprzejezdna za Dorohuskim. Objazd przez Rejowiec.',
     '2026-04-14T09:10:00Z',
     ST_SetSRID(ST_MakePoint(23.5200, 51.3800), 4326),
     ARRAY['Włodawa', 'Chełm', 'Dorohusk', 'Rejowiec'],
     ARRAY['droga', 'zamkniete'],
     0.85, FALSE, 'syntetyczne');
```

---

## 8. Pliki GeoJSON — granice administracyjne

Pliki GeoJSON granic służą do renderowania mapy. Przechowywane po stronie backendu
i serwowane przez `GeoService.java` przez endpoint `GET /api/layers/L-00` (granice).

**Lokalizacja:** `backend/src/main/resources/geojson/`

```
backend/src/main/resources/geojson/
├── lublin_powiaty.geojson      # granice 24 powiatów + 4 miast na prawach powiatu
├── lublin_gminy.geojson        # granice 213 gmin
└── README.md                   # instrukcja pobrania plików
```

### Instrukcja pobrania — `lublin_powiaty.geojson`

```bash
# Źródło: GADM 4.1 (University of California Davis) — open data
# Pobierz plik z podziałem na poziomie powiatów (ADM2):
curl -L "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_POL_2.json" \
  -o /tmp/gadm41_POL_2.json

# Odfiltruj tylko województwo lubelskie (GID_1 = "POL.6_1")
# Wymaga narzędzia jq:
jq '{type: .type, features: [.features[] | select(.properties.GID_1 == "POL.6_1")]}' \
  /tmp/gadm41_POL_2.json \
  > backend/src/main/resources/geojson/lublin_powiaty.geojson

# Weryfikacja — powinno być 28 features (24 powiaty + 4 miasta na prawach powiatu)
jq '.features | length' backend/src/main/resources/geojson/lublin_powiaty.geojson
```

### Instrukcja pobrania — `lublin_gminy.geojson`

```bash
# Poziom gmin (ADM3):
curl -L "https://geodata.ucdavis.edu/gadm/gadm4.1/json/gadm41_POL_3.json" \
  -o /tmp/gadm41_POL_3.json

jq '{type: .type, features: [.features[] | select(.properties.GID_1 == "POL.6_1")]}' \
  /tmp/gadm41_POL_3.json \
  > backend/src/main/resources/geojson/lublin_gminy.geojson

# Weryfikacja — powinno być 217 features (213 gmin + 4 miasta)
jq '.features | length' backend/src/main/resources/geojson/lublin_gminy.geojson
```

### Fallback dla v1.0 — gdy pobieranie niemożliwe

Jeśli pliki GADM są niedostępne, utwórz minimalny plik z bounding boxem województwa.
Mapa i markery DPS-ów będą działać — granice administracyjne doprecyzuj w v1.1.

```bash
cat > backend/src/main/resources/geojson/lublin_powiaty.geojson << 'EOF'
{
  "type": "FeatureCollection",
  "features": [{
    "type": "Feature",
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[21.60, 50.20],[24.20, 50.20],[24.20, 51.90],[21.60, 51.90],[21.60, 50.20]]]
    },
    "properties": { "nazwa": "Województwo Lubelskie", "GID_1": "POL.6_1" }
  }]
}
EOF
```

### Format właściwości feature po normalizacji

`GeoService.java` normalizuje właściwości GADM do spójnego formatu przed serwirowaniem:

```json
{
  "properties": {
    "kod_teryt": "0601",
    "nazwa": "Lublin",
    "typ": "miasto_na_prawach_powiatu",
    "wojewodztwo": "lubelskie"
  }
}
```

---

## 9. Konfiguracja IKE — `ike.config.json`

**Lokalizacja:** `frontend/src/config/ike.config.json`
**Wczytywany przez:** `IkeService.java` przez `@PostConstruct` przy starcie backendu.
**Udostępniany frontendu przez:** `GET /api/ike/config`

Zmiana wag nie wymaga rekompilacji — tylko restartu backendu
lub wywołania `POST /api/ike/recalculate`.

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

Pełny opis edge case'ów i przykłady obliczeń: `docs/IKE_ALGORITHM.md`.
