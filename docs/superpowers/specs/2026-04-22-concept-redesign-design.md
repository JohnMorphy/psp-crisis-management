# Design: Przeprojektowanie koncepcji aplikacji

| | |
|---|---|
| **Data** | 2026-04-22 |
| **Status** | Zatwierdzony do implementacji |
| **Dotyczy** | CLAUDE.md, docs/*, BACKLOG.md, frontend, backend, SQL schema |

---

## 1. Kontekst i motywacja

Aplikacja przechodzi zmianę koncepcji. Stara wersja skupiała się na ewakuacji DPS-ów
w województwie lubelskim z sztucznym algorytmem IKE. Nowa wersja to **ogólnopolski
dashboard jednostek ochrony ludności** — narzędzie sytuacyjne z rozszerzeniem operacyjnym
opartym na realnych alertach zagrożeń.

Zmiana jest zbyt głęboka, żeby łatać istniejące zadania. Strategia: **Revision bloki**
wstawiane między iteracje — każdy revision usuwa legacy i aktualizuje dokumenty,
zanim agent kodowania zacznie nowe zadania.

---

## 2. Nowa definicja produktu

**Czym jest:** Ogólnopolski dashboard geospatialny jednostek ochrony ludności.
Mapuje i prezentuje jednostki (PSP, OSP, PRM/ZRM, Policja, WOT, DPS i inne)
importowane z rzeczywistych publicznych API i rejestrów.

**Czym nie jest:** Narzędzie do planowania ewakuacji DPS-ów. Nie zawiera sztucznego
scoringu IKE ani syntetycznych stref zagrożeń.

**Użytkownik docelowy:** Operator kryzysowy / koordynator zasobów na poziomie
województwa lub kraju. Duży monitor lub tablet.

**Dwa tryby działania:**
1. **Sytuacyjny (baza):** przegląd jednostek na mapie, filtrowanie po typie i zasobach,
   szczegóły jednostki w popupie.
2. **Operacyjny (rozszerzenie):** realny alert zagrożenia (np. IMGW: poziom wody > próg)
   → automatyczne wskazanie jednostek w zasięgu alertu z ich zasobami.

---

## 3. Co usuwamy (legacy scope)

### 3.1 Backend

| Plik | Powód |
|---|---|
| `model/Placowka.java` + `PlacowkaRepository.java` | Zastąpiony przez `entity_registry` |
| `agent/IkeAgent.java` | Sztuczny algorytm |
| `model/IkeResult.java` + `IkeResultRepository.java` | Dependency IKE |
| `controller/IkeController.java` | Dependency IKE |
| `model/StrefaZagrozen.java` + `StrefaZagrozenRepository.java` | Tylko dane testowe |
| `model/MiejsceRelokacji.java` + `MiejsceRelokacjiRepository.java` | Tylko dane testowe |
| `model/ZasobTransportu.java` + `ZasobTransportuRepository.java` | Zastąpiony przez `entity_resources` |
| `resources/ike.config.json` | Config IKE |

### 3.2 Baza danych — tabele do usunięcia z `01_schema.sql`

```
placowka, strefy_zagrozen, miejsca_relokacji, zasob_transportu,
drogi_ewakuacyjne, biale_plamy, ike_results, evacuation_decisions
```

### 3.3 Seed files — do usunięcia

```
02_seed_dps.sql, 04_seed_relokacja.sql, 05_seed_strefy.sql, 06_seed_transport.sql
```

### 3.4 Frontend

| Plik | Akcja |
|---|---|
| `layers/ThreatZoneLayer.tsx` | Usuń |
| `EntityPopup.tsx` | Usuń pola IKE (ike_score, ike_kategoria, kolor IKE) |
| `EntityLayer.tsx` | Usuń logikę kolorowania po IKE |
| `EntityFilterPanel.tsx` | Usuń filtr IKE kategorii — zastąp filtrem zasobów |
| `mapStore.ts` | Usuń stan IKE |

### 3.5 Dokumenty — do aktualizacji/usunięcia

- `docs/IKE_ALGORITHM.md` → **usuń plik**
- `docs/PRD.md` → przepisz cel, zakres, persony
- `docs/ARCHITEKTURA_PLAN.md` → nowi agenci, nowy diagram, nowe tabele
- `docs/DATA_SCHEMA.md` → usuń stare tabele, dodaj nowe
- `docs/API_REFERENCE.md` → usuń IKE endpoints, dodaj nowe
- `CLAUDE.md` → nowy cel projektu, nowe reguły logowania/testowania

---

## 4. Nowy model danych

### 4.1 `resource_type` — słownik typów zasobów

```sql
CREATE TABLE resource_type (
    code            VARCHAR(80) PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    category        VARCHAR(50),   -- 'pojazd', 'sprzet_wodny', 'sprzet_gasniczy', 'medyczny'
    unit_of_measure VARCHAR(20) DEFAULT 'szt'
);
```

Seed: ~50 mockowanych typów (woz_cysternowy, ponton_motorowy, agregat_pradotworczy,
nosze_transportowe, itd.)

### 4.2 `entity_resources` — zasoby jednostek (wiele-do-wielu z entity_registry)

```sql
CREATE TABLE entity_resources (
    id                  BIGSERIAL PRIMARY KEY,
    entity_id           BIGINT NOT NULL REFERENCES entity_registry(id) ON DELETE CASCADE,
    resource_type_code  VARCHAR(80) NOT NULL REFERENCES resource_type(code),
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    is_available        BOOLEAN DEFAULT TRUE,
    last_updated        TIMESTAMPTZ DEFAULT NOW(),
    source              VARCHAR(20) DEFAULT 'mock',
    UNIQUE (entity_id, resource_type_code)
);
CREATE INDEX ON entity_resources(entity_id);
CREATE INDEX ON entity_resources(resource_type_code);
```

Umożliwia filtrowanie operacyjne:
```sql
SELECT DISTINCT er.entity_id
FROM entity_resources er
JOIN entity_registry e ON e.id = er.entity_id
WHERE er.resource_type_code = 'ponton_motorowy'
  AND er.is_available = TRUE
  AND ST_DWithin(e.geom::geography, ST_MakePoint(:lon,:lat)::geography, :radius_m)
```

### 4.3 `threat_alert` — alerty zagrożeń z rzeczywistych API

```sql
CREATE TABLE threat_alert (
    id             BIGSERIAL PRIMARY KEY,
    source_api     VARCHAR(50) NOT NULL,   -- 'imgw_hydro', 'imgw_meteo', 'manual'
    external_id    VARCHAR(120),           -- ID stacji w źródle
    threat_type    VARCHAR(30) NOT NULL,   -- 'flood', 'fire', 'blackout'
    level          VARCHAR(10) NOT NULL    -- 'warning', 'alarm', 'emergency'
                   CHECK (level IN ('warning', 'alarm', 'emergency')),
    value          DECIMAL(10,3),          -- np. poziom wody w cm
    threshold      DECIMAL(10,3),          -- przekroczony próg
    unit           VARCHAR(20),            -- 'cm', '%'
    geom           GEOMETRY(Point, 4326),
    radius_km      DECIMAL(6,1),
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ,
    is_active      BOOLEAN DEFAULT TRUE,
    correlation_id VARCHAR(36),
    raw_data       JSONB DEFAULT '{}'
);
CREATE INDEX ON threat_alert USING GIST(geom);
CREATE INDEX ON threat_alert(is_active, threat_type);
```

Zastępuje `strefy_zagrozen` — dane z realnych API, nie syntetyczne poligony.

---

## 5. Nowa architektura — łańcuch eventów

### 5.1 Diagram

```
@Scheduled ThreatAlertImportAgent  ← polling IMGW API co N minut
    → poziom wody > próg alarmowy
    → INSERT do threat_alert (is_active=true)
    → publisher.publishEvent(new ThreatAlertEvent(...))    [202]

        [@Async] NearbyUnitsAgent.onThreatAlert()
            → PostGIS ST_DWithin: entity_registry w radius_km od geom alertu
            → opcjonalnie filtr po resource_type_code
            → publisher.publishEvent(new NearbyUnitsComputedEvent(...))

        [@Async] LiveFeedService.onThreatAlert()
            → push /topic/threat-alerts

        [@Async] LiveFeedService.onNearbyUnitsComputed()
            → push /topic/nearby-units
```

### 5.2 Nowi agenci

| Stary | Nowy | Różnica |
|---|---|---|
| `FloodImportAgent` (syntetyczny) | `ThreatAlertImportAgent` | Polling IMGW API + manual trigger |
| `IkeAgent` (sztuczny scoring) | `NearbyUnitsAgent` | PostGIS ST_DWithin, zero scoringu |
| `DecisionAgent` (fake rekomendacje) | *(usunięty)* | Operator decyduje sam na podstawie mapy |
| `LiveFeedService` | `LiveFeedService` (uproszczony) | Słucha ThreatAlertEvent + NearbyUnitsComputedEvent |

### 5.3 Nowe eventy

```java
ThreatAlertEvent {
    String alertId;       // ID z threat_alert
    String threatType;    // 'flood', 'fire', 'blackout'
    String level;         // 'warning', 'alarm', 'emergency'
    String sourceApi;     // 'imgw_hydro', 'manual'
    Double lat, lon;
    Double radiusKm;
    String correlationId;
}

NearbyUnitsComputedEvent {
    String correlationId;
    List<Long> entityIds;  // ID jednostek w zasięgu
    String alertId;
}
```

---

## 6. Publiczne API — priorytety integracji

| Źródło | Dane | Format | Coords | Priorytet |
|---|---|---|---|---|
| IMGW Hydro `danepubliczne.imgw.pl/api/data/hydro` | Odczyty stacji, stany alarmowe | REST JSON | ✅ | **P1 — v1.1** |
| IMGW Meteo `danepubliczne.imgw.pl/api/data/synop` | Ostrzeżenia meteo | REST JSON | ✅ | P2 |
| PSP `dane.gov.pl/dataset/1050` | Adresy jednostek PSP | CSV bulk | ⚠️ geokodowanie | **P1 — v1.1** |
| PRM `rjwprm.ezdrowie.gov.pl` | Jednostki ZRM, SOR | XLSX/XML daily | ⚠️ geokodowanie | **P1 — v1.1** |
| RPWDL `ezdrowie.gov.pl/rejestry` | Podmioty lecznicze | XLSX/XML | ⚠️ geokodowanie | P2 |
| GIS PSP ArcGIS `strazpozarna.maps.arcgis.com` | Mapa PSP/OSP | ArcGIS/WFS | ✅ | P2 — zbadać |
| geoportal.gov.pl WFS | Placówki zdrowia | WFS | ✅ | P2 |
| OSP KSRG | Lista OSP w KSRG | scraping KG PSP | ⚠️ | P3 |

**Wniosek:** IMGW to jedyne źródło z REST API do pollingu real-time. Pozostałe = periodic
bulk import (CSV/XLSX download → parse → upsert + geokodowanie Nominatim OSM).

---

## 7. Nowe reguły agentowe (dodać do CLAUDE.md)

```
### Logi i testy — obowiązek na każdym zadaniu backendowym

Każdy nowy lub modyfikowany serwis/agent musi zawierać:
1. @Slf4j — SLF4J logger
2. log.info("[NazwaKlasy] opis — correlationId={}", id) na wejściu key metod
3. log.error("[NazwaKlasy] błąd — correlationId={}, msg={}", id, e.getMessage())
4. src/test/java/ — @ExtendWith(MockitoExtension.class), pokrycie logiki biznesowej
   (nie getterów, nie repozytoriów)

Frontend: vitest dla funkcji w utils/ i hooks/ z logiką (nie czysty fetch).
Brak testów = zadanie nie jest ukończone.
```

---

## 8. Nowa struktura BACKLOG

### Sekwencja zmian

Kolejność: REVISION 2 przed REVISION 1 — czyścimy legacy zanim naprawiamy UX.

```
v1.0 UKOŃCZONA (1.1–1.12) ✅

REVISION 2 (nowy, wstawić PRZED REVISION 1) — Usunięcie legacy
  R2.1 — Backend legacy removal + SQL schema cleanup + seed files
  R2.2 — Frontend legacy removal (ThreatZoneLayer, IKE refs)
  R2.3 — Docs update (PRD, ARCHITEKTURA_PLAN, DATA_SCHEMA, API_REFERENCE,
          CLAUDE.md, usuń IKE_ALGORITHM.md)
  Uwaga: R2.3 musi też zaktualizować 03_seed_layers.sql — usunąć
  warstwy L-01…L-07 (legacy), zachować L-08/L-09/L-10.

REVISION 1 (istniejący, po REVISION 2) — UX fixes
  + dodać: fix layer selection style per warstwa
  + dodać: viewport startowy → cała Polska, minZoom=5, maxBounds

DŁUG TECHNICZNY (przed v1.1)
  DT-LOGS-TESTS — Logi + testy jednostkowe dla istniejących serwisów
  (AdminBoundaryImportAgent, EntityImportService, EntityRegistryService,
   AdminBoundaryService, GeoService)

v1.1 — Zasoby + Alerty zagrożeń
  2.1 — SQL: resource_type + entity_resources + threat_alert
  2.2 — Seed: ~50 typów zasobów + mockowane zasoby dla istniejących jednostek
  2.3 — ThreatAlertImportAgent: IMGW hydro polling + ThreatAlertEvent
  2.4 — NearbyUnitsAgent: PostGIS ST_DWithin + NearbyUnitsComputedEvent
  2.5 — LiveFeedService: push /topic/threat-alerts + /topic/nearby-units
  2.6 — Frontend: ThreatAlertLayer (punkty alertów na mapie)
  2.7 — Frontend: resource filter w EntityFilterPanel
  2.8 — Frontend: NearbyUnitsPanel (lista jednostek w zasięgu aktywnego alertu)
  2.9 — Frontend: manual threat trigger UI

v1.2 — Importy z publicznych API
  3.1 — PSP bulk import (CSV dane.gov.pl + geokodowanie Nominatim, rate-limit 1 req/s)
  3.2 — PRM/ZRM bulk import (XLSX rjwprm.ezdrowie.gov.pl + geokodowanie Nominatim)
  3.3 — RPWDL import (podmioty lecznicze — XLSX ezdrowie.gov.pl + geokodowanie)
  3.4 — Clustering jednostek na mapie (supercluster — wymagane > 1000 punktów)

v1.3 — UX i głos (bez zmian w koncepcji)
  4.1 — Asystent głosowy
  4.2 — Docker stack produkcyjny
```

---

## 9. Poprawki UX

### 9.1 Fix: layer selection style per warstwa

**Problem:** jeden `selectedRegion` w mapStore podświetla jednocześnie wiele warstw.

**Rozwiązanie:**
```typescript
// mapStore.ts
selectedFeatureByLayer: Record<string, string | null>
// { 'L-08': 'lubelskie', 'L-09': null, 'L-10': null }

setSelectedFeature(layerId: string, featureId: string | null)
// zaznaczenie L-09 czyści L-10; zaznaczenie L-08 czyści L-09 + L-10
```

### 9.2 Fix: viewport dla całej Polski + ograniczenia zoom

```typescript
// MapContainer.tsx
center={[52.0, 19.5]}
zoom={6}
minZoom={5}
maxBounds={[[48.9, 13.9], [55.0, 25.0]]}
maxBoundsViscosity={0.8}
```

### 9.3 Clustering — backlog v1.2

Warunek wstępny: zaimportowane dane z PSP/PRM API (> 1000 punktów).
Biblioteka: supercluster lub Leaflet.markercluster.
Logika: zoom < 9 → klastry; zoom ≥ 11 → pojedyncze markery.

---

## 10. Co zostaje bez zmian

- `entity_registry` + powiązane modele/repozytoria/serwisy ✅
- `granice_administracyjne` + AdminBoundaryImportAgent ✅
- `layer_config` tabela ✅
- `AdminBoundaryLayer.tsx`, `RegionInfoPanel.tsx` ✅
- `notificationStore.ts` ✅
- WebSocket infrastruktura (basic) ✅
- Stack technologiczny (React-Leaflet, Zustand, Spring Boot, PostGIS) ✅
