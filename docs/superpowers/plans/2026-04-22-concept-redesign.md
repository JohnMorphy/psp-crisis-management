# Concept Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Przeprojektować aplikację z "ewakuacja DPS woj. lubelskiego" na "ogólnopolski dashboard jednostek ochrony ludności" — usunąć legacy kod/test-only features, zaktualizować dokumentację i BACKLOG z nowymi zadaniami dla przyszłych sesji agentowych.

**Architecture:** Revision-block strategy: najpierw usuwamy legacy (REVISION 2), potem UX fixes (REVISION 1), potem definiujemy nowe zadania v1.1–v1.2 w BACKLOG.md. Każdy blok kończy się działającą aplikacją.

**Tech Stack:** Spring Boot 3 / OpenJDK 21 / React 18 / PostGIS 15 / Zustand / Tailwind CSS / Maven

---

## File Structure Map

### Tworzone
- `docs/superpowers/plans/2026-04-22-concept-redesign.md` ← ten plik

### Usuwane (dokumenty)
- `docs/IKE_ALGORITHM.md`

### Usuwane (backend — REVISION 2)
- `backend/src/main/java/pl/lublin/dashboard/model/Placowka.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/PlacowkaRepository.java`
- `backend/src/main/java/pl/lublin/dashboard/agent/IkeAgent.java`
- `backend/src/main/java/pl/lublin/dashboard/model/IkeResult.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/IkeResultRepository.java`
- `backend/src/main/java/pl/lublin/dashboard/controller/IkeController.java`
- `backend/src/main/java/pl/lublin/dashboard/model/StrefaZagrozen.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/StrefaZagrozenRepository.java`
- `backend/src/main/java/pl/lublin/dashboard/model/MiejsceRelokacji.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/MiejsceRelokacjiRepository.java`
- `backend/src/main/java/pl/lublin/dashboard/model/ZasobTransportu.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/ZasobTransportuRepository.java`
- `backend/src/main/resources/ike.config.json`
- `backend/src/main/resources/db/02_seed_dps.sql`
- `backend/src/main/resources/db/04_seed_relokacja.sql`
- `backend/src/main/resources/db/05_seed_strefy.sql`
- `backend/src/main/resources/db/06_seed_transport.sql`

### Usuwane (frontend — REVISION 2)
- `frontend/src/components/map/layers/ThreatZoneLayer.tsx`

### Modyfikowane (dokumenty)
- `CLAUDE.md`
- `docs/PRD.md`
- `docs/ARCHITEKTURA_PLAN.md`
- `docs/DATA_SCHEMA.md`
- `docs/API_REFERENCE.md`
- `docs/BACKLOG.md`

### Modyfikowane (backend — REVISION 2)
- `backend/src/main/resources/db/01_schema.sql`
- `backend/src/main/java/pl/lublin/dashboard/service/GeoService.java`
- `backend/src/main/java/pl/lublin/dashboard/service/EntityRegistryService.java`
- `backend/src/main/resources/db/03_seed_layers.sql`

### Modyfikowane (frontend — REVISION 2)
- `frontend/src/components/map/MapContainer.tsx`
- `frontend/src/components/map/layers/EntityLayer.tsx`
- `frontend/src/components/map/EntityPopup.tsx`
- `frontend/src/types/gis.ts`

### Modyfikowane (frontend — REVISION 1)
- `frontend/src/store/mapStore.ts`
- `frontend/src/components/map/layers/AdminBoundaryLayer.tsx`

---

## Task 1: Zaktualizuj CLAUDE.md — nowy cel projektu i reguły

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Krok 1: Zmień sekcję "Czym jest ten projekt"**

Zastąp istniejącą sekcję `## Czym jest ten projekt`:

```markdown
## Czym jest ten projekt

Ogólnopolski Geospatial Dashboard Jednostek Ochrony Ludności.

Aplikacja mapuje i prezentuje jednostki ochrony ludności (PSP, OSP, ZRM/PRM, Policja,
WOT, DPS i inne) importowane z rzeczywistych publicznych rejestrów i API.
Służy operatorom kryzysowym do przeglądu dostępnych zasobów w danym obszarze.

**Tryb sytuacyjny (baza):** przegląd jednostek na mapie, filtrowanie po typie i zasobach,
szczegóły jednostki w popupie.

**Tryb operacyjny (rozszerzenie v1.1+):** realny alert zagrożenia (np. IMGW: poziom wody
> próg alarmowy) → automatyczne wskazanie jednostek w zasięgu alertu z ich zasobami.

System odpowiada na pytanie:
„Jakie jednostki ochrony ludności i zasoby są dostępne w zagrożonym obszarze?"
```

- [ ] **Krok 2: Zaktualizuj tabelę agentów w sekcji "Agenci — odpowiedzialności"**

Zastąp całą tabelę agentów:

```markdown
| Agent / Serwis | Odpowiedzialność | Wyzwalacz |
|---|---|---|
| `AdminBoundaryImportAgent` | Import granic adm. z GUGiK PRG WFS | HTTP `POST /api/admin-boundaries/import` |
| `ThreatAlertImportAgent` | Polling IMGW API + manual trigger → zapis do `threat_alert` → `ThreatAlertEvent` | `@Scheduled` co N minut + HTTP `POST /api/threats/manual` |
| `NearbyUnitsAgent` | PostGIS ST_DWithin: jednostki w zasięgu alertu → `NearbyUnitsComputedEvent` | `@EventListener ThreatAlertEvent` |
| `LiveFeedService` | Push alertów i pobliskich jednostek przez WebSocket | `@EventListener ThreatAlertEvent` + `@EventListener NearbyUnitsComputedEvent` |
```

- [ ] **Krok 3: Zaktualizuj tabelę "Status projektu"**

```markdown
| Iteracja | Status | Deliverable |
|---|---|---|
| v1.0 — Fundament GIS | ✅ Ukończona (1.1–1.12 ✅) | Mapa + granice (PL) + entity registry + Spring Boot + PostGIS |
| REVISION 2 | ⬜ | Legacy removal (IKE, DPS tables, test-only layers) + docs update |
| REVISION 1 | ⬜ | UX fixes: layer selection per warstwa, viewport |
| DT-LOGS-TESTS | ⬜ | Logi + testy dla istniejących serwisów |
| v1.1 — Zasoby + Alerty | ⬜ | resource_type, entity_resources, threat_alert, ThreatAlertImportAgent (IMGW), NearbyUnitsAgent, WebSocket |
| v1.2 — Importy API | ⬜ | PSP, PRM, RPWDL bulk import + Nominatim geokodowanie + clustering |
```

- [ ] **Krok 4: Dodaj nową sekcję "Logi i testy" przed sekcją "Reguły pracy agentowej"**

```markdown
## Logi i testy — obowiązek na każdym zadaniu

Każdy nowy lub modyfikowany serwis/agent backendu MUSI zawierać:

1. `@Slf4j` lub `private static final Logger log = LoggerFactory.getLogger(X.class);`
2. `log.info("[NazwaKlasy] opis — correlationId={}", id)` na wejściu kluczowych metod
3. `log.error("[NazwaKlasy] błąd — correlationId={}, msg={}", id, e.getMessage())` w catch
4. Testy jednostkowe w `src/test/java/` z `@ExtendWith(MockitoExtension.class)`
   — pokrycie każdej metody z logiką biznesową (nie getterów, nie repozytoriów)

Frontend: vitest dla funkcji w `utils/` i `hooks/` z logiką (nie czysty fetch).

**Brak testów = zadanie nie jest ukończone.**
```

- [ ] **Krok 5: Zaktualizuj sekcję "Kluczowe pliki" — usuń wiersze dotyczące IKE**

Usuń wiersze:
```
| Logika IKE | `backend/.../agent/IkeAgent.java` + `docs/IKE_ALGORITHM.md` |
| Import WFS | `backend/.../agent/FloodImportAgent.java` |
| Rekomendacje | `backend/.../agent/DecisionAgent.java` |
| Wagi IKE | `backend/src/main/resources/ike.config.json` (frontend pobiera przez `GET /api/ike/config`) |
```

Dodaj wiersze:
```
| Alert zagrożenia | `backend/.../agent/ThreatAlertImportAgent.java` + tabela `threat_alert` |
| Pobliskie jednostki | `backend/.../agent/NearbyUnitsAgent.java` |
| Zasoby jednostek | tabela `entity_resources` + `resource_type` |
```

- [ ] **Krok 6: Commit**

```
docs(CLAUDE.md): nowy cel projektu + reguły logowania/testowania + zaktualizowani agenci
```

---

## Task 2: Usuń IKE_ALGORITHM.md i zaktualizuj PRD.md

**Files:**
- Delete: `docs/IKE_ALGORITHM.md`
- Modify: `docs/PRD.md`

- [ ] **Krok 1: Usuń plik IKE_ALGORITHM.md**

```bash
rm "docs/IKE_ALGORITHM.md"
```

- [ ] **Krok 2: Zastąp sekcję §1 "Cel produktu" w PRD.md**

```markdown
## 1. Cel produktu

Ogólnopolski, interaktywny dashboard geospatialny jednostek ochrony ludności
umożliwiający operatorom kryzysowym przegląd dostępnych zasobów w czasie rzeczywistym.

### Problem

Dane o jednostkach ochrony ludności (PSP, OSP, ZRM, Policja, WOT i inne) są
rozproszone w kilkudziesięciu systemach i rejestrach. W sytuacji kryzysowej operator
musi ręcznie zbierać informacje z wielu źródeł. Ta fragmentacja kosztuje czas.

### Mierzalne cele sukcesu

| KPI | Cel |
|---|---|
| Czas ładowania mapy | < 3 s przy łączu 10 Mbps |
| Czas odpowiedzi API (p95) | < 200 ms |
| Czas od alertu IMGW do aktualizacji mapy | < 30 s |
| Pokrycie jednostek PSP w bazie | 100% po imporcie z dane.gov.pl |
| Zmiana warstwy / filtra w UI | < 500 ms |
```

- [ ] **Krok 3: Zastąp sekcję §3.1 "W zakresie" w PRD.md**

```markdown
### 3.1 W zakresie (in-scope)

- Interaktywna mapa GIS całej Polski (województwa, powiaty, gminy)
- Wielowarstwowe nakładki danych: jednostki ochrony ludności wg kategorii
- Import jednostek z publicznych API i rejestrów (PSP, PRM, RPWDL)
- Zasoby jednostek (mockowane + docelowo z rejestru) — filtrowanie po typie zasobu
- Alerty zagrożeń z IMGW (poziomy wód) i ręczny trigger operatora
- Przestrzenne wskazanie jednostek w zasięgu aktywnego alertu
- Dynamiczne odświeżanie (WebSocket)
- Responsywne UI na duży monitor / tablet
- Asystent głosowy (v1.3)
```

- [ ] **Krok 4: Zastąp sekcję §3.2 "Poza zakresem" w PRD.md**

```markdown
### 3.2 Poza zakresem (out-of-scope)

- Automatyczne rekomendacje ewakuacyjne (operator decyduje sam)
- Sztuczny scoring ryzyka (IKE)
- Integracja z systemami operacyjnymi służb (SWD PSP)
- Moduł autentykacji i zarządzania użytkownikami
- Obsługa danych niejawnych / wrażliwych (RODO)
- Wersja mobilna (smartfon)
```

- [ ] **Krok 5: Commit**

```
docs(PRD): nowy cel + zakres — ogólnopolski dashboard jednostek ochrony ludności
```

---

## Task 3: Zaktualizuj ARCHITEKTURA_PLAN.md

**Files:**
- Modify: `docs/ARCHITEKTURA_PLAN.md`

- [ ] **Krok 1: Zastąp diagram architektury (sekcja §3)**

W sekcji `## 3. Diagram architektury` zastąp listę agentów w diagramie:

```
│  ├── AdminBoundaryImportAgent (PRG WFS → granice_adm.)       │
│  ├── ThreatAlertImportAgent (@Scheduled IMGW + manual)        │
│  │       └──publishes──► ThreatAlertEvent                     │
│  ├── NearbyUnitsAgent ◄── @EventListener(ThreatAlertEvent)    │
│  │       └──publishes──► NearbyUnitsComputedEvent             │
│  └── LiveFeedService ◄── ThreatAlertEvent + NearbyUnitsComputedEvent │
```

- [ ] **Krok 2: Zastąp sekcje 4.1–4.5 (stare eventy i agenci) nową treścią**

Usuń sekcje `4.1 ThreatUpdatedEvent`, `4.1a IkeRecalculatedEvent`, `4.2 FloodImportAgent`,
`4.3 IkeAgent`, `4.4 DecisionAgent`, `4.5 LiveFeedService`.

Wstaw:

```markdown
### 4.1 `ThreatAlertEvent`

```java
public class ThreatAlertEvent extends ApplicationEvent {
    private final String alertId;       // UUID / ID z threat_alert
    private final String threatType;    // "flood" | "fire" | "blackout"
    private final String level;         // "warning" | "alarm" | "emergency"
    private final String sourceApi;     // "imgw_hydro" | "manual"
    private final Double lat;
    private final Double lon;
    private final Double radiusKm;
    private final String correlationId;
}
```

### 4.2 `NearbyUnitsComputedEvent`

```java
public class NearbyUnitsComputedEvent extends ApplicationEvent {
    private final String correlationId;
    private final List<Long> entityIds;  // ID jednostek w zasięgu
    private final String alertId;
}
```

### 4.3 `ThreatAlertImportAgent`

```
Odpowiedzialność: polling IMGW hydro API → wykrycie przekroczenia progu → zapis threat_alert

Wyzwalacz: @Scheduled (co N minut z application.yml) + HTTP POST /api/threats/manual

Algorytm:
1. GET https://danepubliczne.imgw.pl/api/data/hydro → lista stacji z odczytami
2. Dla każdej stacji z poziomem > progu ostrzegawczego:
   a. Sprawdź czy alert już istnieje (external_id + is_active=true) — jeśli tak, pomiń
   b. INSERT do threat_alert (source_api='imgw_hydro', threat_type='flood', level, geom, ...)
   c. publisher.publishEvent(new ThreatAlertEvent(...))
3. Oznacz nieaktywne alerty (stacje poniżej progu) jako is_active=false
```

### 4.4 `NearbyUnitsAgent`

```
Odpowiedzialność: PostGIS ST_DWithin → lista jednostek w zasięgu alertu

Wyzwalacz: @EventListener(ThreatAlertEvent) + @Async

Algorytm:
1. SELECT entity_registry WHERE ST_DWithin(geom, alert.geom, radiusKm * 1000)
2. Opcjonalnie filtruj po resource_type_code (z parametru eventu)
3. publisher.publishEvent(new NearbyUnitsComputedEvent(correlationId, entityIds, alertId))
```

### 4.5 `LiveFeedService`

```
Wyzwalacze:
  @EventListener(ThreatAlertEvent)        → push /topic/threat-alerts
  @EventListener(NearbyUnitsComputedEvent) → push /topic/nearby-units
```
```

- [ ] **Krok 3: Zaktualizuj tabelę iteracji w §9**

Zastąp tabelę:

```markdown
| Iteracja | Cel |
|---|---|
| **v1.0 — Fundament GIS** | Mapa, entity registry, granice PRG. ✅ |
| **REVISION 2** | Usunięcie legacy (IKE, DPS, test-only layers). |
| **REVISION 1** | UX fixes: layer selection, viewport. |
| **DT-LOGS-TESTS** | Logi + testy dla istniejących serwisów. |
| **v1.1 — Zasoby + Alerty** | resource_type, entity_resources, threat_alert, ThreatAlertImportAgent (IMGW), NearbyUnitsAgent, WebSocket push. |
| **v1.2 — Importy API** | PSP/PRM/RPWDL bulk import + geokodowanie Nominatim + clustering. |
| **v1.3 — UX i głos** | Asystent głosowy + Docker prod. |
```

- [ ] **Krok 4: Commit**

```
docs(ARCHITEKTURA_PLAN): nowi agenci — ThreatAlertImportAgent, NearbyUnitsAgent, nowe eventy
```

---

## Task 4: Zaktualizuj DATA_SCHEMA.md

**Files:**
- Modify: `docs/DATA_SCHEMA.md`

- [ ] **Krok 1: Usuń sekcje dotyczące usuniętych tabel**

Usuń sekcje DDL dla tabel:
`placowka`, `strefy_zagrozen`, `miejsca_relokacji`, `zasob_transportu`,
`drogi_ewakuacyjne`, `biale_plamy`, `ike_results`, `evacuation_decisions`

Usuń też sekcje seed files dla: `02_seed_dps.sql`, `04_seed_relokacja.sql`,
`05_seed_strefy.sql`, `06_seed_transport.sql`.

- [ ] **Krok 2: Dodaj DDL dla nowych tabel**

Wstaw nową sekcję po `entity_registry`:

```markdown
### Tabela: `resource_type`

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

### Tabela: `entity_resources`

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

### Tabela: `threat_alert`

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

[ ] **Krok 3: Commit**

```
docs(DATA_SCHEMA): usuń legacy tables, dodaj resource_type + entity_resources + threat_alert
```

---

## Task 5: Zaktualizuj API_REFERENCE.md

**Files:**
- Modify: `docs/API_REFERENCE.md`

- [ ] **Krok 1: Usuń sekcje IKE**

Usuń sekcje:
- `GET /api/ike`
- `GET /api/ike/{kod}`
- `GET /api/ike/config`
- `POST /api/ike/recalculate`
- `POST /api/threat/flood/import`
- `POST /api/threat/clear`
- `POST /api/calculate/transport`
- `POST /api/calculate/relocation`
- `POST /api/calculate/threat`
- WebSocket topics: `/topic/ike`, `/topic/decisions`, `/topic/layers/L-03`

- [ ] **Krok 2: Dodaj nowe endpointy**

```markdown
### `GET /api/entity-resources?entityId={id}`

Zasoby konkretnej jednostki.

**Response 200:**
```json
{
  "entity_id": 42,
  "resources": [
    { "resource_type_code": "woz_cysternowy", "name": "Wóz cysternowy", "quantity": 2, "is_available": true },
    { "resource_type_code": "ponton_motorowy", "name": "Ponton motorowy", "quantity": 1, "is_available": true }
  ]
}
```

### `GET /api/resource-types`

Lista wszystkich typów zasobów (słownik).

### `GET /api/threats/active`

Lista aktywnych alertów zagrożeń.

**Response 200:**
```json
{
  "count": 2,
  "alerts": [
    {
      "id": 1,
      "source_api": "imgw_hydro",
      "threat_type": "flood",
      "level": "alarm",
      "value": 520.0,
      "threshold": 480.0,
      "unit": "cm",
      "lat": 51.24,
      "lon": 22.57,
      "radius_km": 30.0,
      "detected_at": "2026-04-22T10:00:00Z"
    }
  ]
}
```

### `POST /api/threats/manual`

Operator ręcznie tworzy alert.

**Request:**
```json
{ "threat_type": "flood", "level": "warning", "lat": 51.2, "lon": 22.5, "radius_km": 25.0, "description": "Ręczny alert" }
```

**Response 202:**
```json
{ "status": "started", "alert_id": 5, "correlation_id": "uuid" }
```

### `GET /api/nearby-units?alertId={id}`

Jednostki w zasięgu alertu (wynik NearbyUnitsAgent).

### WebSocket topics (nowe)

| Topic | Payload | Kiedy |
|---|---|---|
| `/topic/threat-alerts` | lista aktywnych alertów | po ThreatAlertEvent |
| `/topic/nearby-units` | lista entityIds + alertId | po NearbyUnitsComputedEvent |

- [ ] **Krok 3: Commit**

```
docs(API_REFERENCE): usuń IKE/calculate endpoints, dodaj threats/resources/nearby-units
```

---

## Task 6: REVISION 2 — R2.1 Backend legacy removal

**Files:**
- Delete: wszystkie pliki z listy "Usuwane (backend)" w File Structure Map
- Modify: `backend/src/main/resources/db/01_schema.sql`
- Modify: `backend/src/main/java/pl/lublin/dashboard/service/GeoService.java`
- Modify: `backend/src/main/java/pl/lublin/dashboard/service/EntityRegistryService.java`
- Modify: `backend/src/main/resources/db/03_seed_layers.sql`

- [ ] **Krok 1: Usuń pliki modeli i repozytoriów**

```bash
rm backend/src/main/java/pl/lublin/dashboard/model/Placowka.java
rm backend/src/main/java/pl/lublin/dashboard/repository/PlacowkaRepository.java
rm backend/src/main/java/pl/lublin/dashboard/agent/IkeAgent.java
rm backend/src/main/java/pl/lublin/dashboard/model/IkeResult.java
rm backend/src/main/java/pl/lublin/dashboard/repository/IkeResultRepository.java
rm backend/src/main/java/pl/lublin/dashboard/controller/IkeController.java
rm backend/src/main/java/pl/lublin/dashboard/model/StrefaZagrozen.java
rm backend/src/main/java/pl/lublin/dashboard/repository/StrefaZagrozenRepository.java
rm backend/src/main/java/pl/lublin/dashboard/model/MiejsceRelokacji.java
rm backend/src/main/java/pl/lublin/dashboard/repository/MiejsceRelokacjiRepository.java
rm backend/src/main/java/pl/lublin/dashboard/model/ZasobTransportu.java
rm backend/src/main/java/pl/lublin/dashboard/repository/ZasobTransportuRepository.java
rm backend/src/main/resources/ike.config.json
rm backend/src/main/resources/db/02_seed_dps.sql
rm backend/src/main/resources/db/04_seed_relokacja.sql
rm backend/src/main/resources/db/05_seed_strefy.sql
rm backend/src/main/resources/db/06_seed_transport.sql
```

- [ ] **Krok 2: Zastąp GeoService.java**

```java
package pl.lublin.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoService.class);

    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityRegistryService entityRegistryService;

    private final GeoJsonWriter geoJsonWriter;
    private Map<String, Object> powiaty;

    public GeoService() {
        geoJsonWriter = new GeoJsonWriter();
        geoJsonWriter.setEncodeCRS(false);
    }

    @PostConstruct
    public void loadBoundaries() {
        powiaty = loadGeoJson("geojson/lublin_powiaty.geojson");
        log.info("[GeoService] Loaded admin boundaries, features: {}",
            ((List<?>) powiaty.getOrDefault("features", Collections.emptyList())).size());
    }

    private Map<String, Object> loadGeoJson(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, Map.class);
        } catch (Exception e) {
            log.warn("[GeoService] Cannot load {} — {}", path, e.getMessage());
            Map<String, Object> fc = new LinkedHashMap<>();
            fc.put("type", "FeatureCollection");
            fc.put("features", Collections.emptyList());
            return fc;
        }
    }

    public Map<String, Object> getAdminBoundaries() {
        return powiaty;
    }

    public Map<String, Object> buildLayerGeoJson(
            String layerId,
            String powiat,
            String gmina,
            String bbox,
            String kodWoj,
            String kodPow,
            String kodGmina,
            String category,
            String source,
            String q
    ) {
        return switch (layerId) {
            case "L-01", "L-02" -> entityRegistryService.buildLayerGeoJson(
                    layerId, category, source, kodWoj, kodPow, kodGmina, bbox, q);
            default -> buildEmptyLayer(layerId);
        };
    }

    private Map<String, Object> buildEmptyLayer(String layerId) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("layer_id", layerId);
        fc.put("ostatnia_aktualizacja", timestamp);
        fc.put("feature_count", 0);
        fc.put("features", Collections.emptyList());
        return fc;
    }

    public Map<String, Object> geometryToMap(Geometry geometry) {
        if (geometry == null) return null;
        try {
            String geoJson = geoJsonWriter.write(geometry);
            return objectMapper.readValue(geoJson, Map.class);
        } catch (Exception e) {
            log.warn("[GeoService] Cannot serialize geometry — {}", e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Krok 3: Zastąp EntityRegistryService.java — usuń IKE**

Usuń z EntityRegistryService.java:
- `import pl.lublin.dashboard.model.IkeResult;`
- `import pl.lublin.dashboard.repository.IkeResultRepository;`
- `@Autowired private IkeResultRepository ikeResultRepository;`

Zastąp metodę `buildLayerGeoJson` (linie 141-171):

```java
public Map<String, Object> buildLayerGeoJson(
        String layerId,
        String category,
        String source,
        String kodWoj,
        String kodPow,
        String kodGmina,
        String bbox,
        String q
) {
    String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    List<EntityRegistryEntry> entries = filterEntries(category, source, kodWoj, kodPow, kodGmina, bbox, q).stream()
            .filter(entry -> entry.getGeom() != null)
            .toList();

    List<Map<String, Object>> features = entries.stream()
            .map(this::toFeature)
            .filter(Objects::nonNull)
            .toList();

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("type", "FeatureCollection");
    response.put("layer_id", layerId);
    response.put("ostatnia_aktualizacja", timestamp);
    response.put("feature_count", features.size());
    response.put("features", features);
    return response;
}
```

Zastąp metodę `toFeature` (linia 245):

```java
private Map<String, Object> toFeature(EntityRegistryEntry entry) {
    Map<String, Object> geometry = geometryToMap(entry.getGeom());
    if (geometry == null) return null;
    Map<String, Object> feature = new LinkedHashMap<>();
    feature.put("type", "Feature");
    feature.put("geometry", geometry);
    feature.put("properties", buildProperties(entry));
    return feature;
}
```

Zastąp metodę `toResponseRow` (linia 258):

```java
private Map<String, Object> toResponseRow(EntityRegistryEntry entry) {
    return buildProperties(entry);
}
```

Zastąp sygnaturę `buildProperties` — usuń parametr `IkeResult ikeResult` (linia 262):

```java
private Map<String, Object> buildProperties(EntityRegistryEntry entry) {
```

Usuń dwie ostatnie linie w metodzie `buildProperties` (linie 296-297):

```java
// USUŃ te dwie linie:
properties.put("ike_score", ikeResult != null ? ikeResult.getIkeScore() : null);
properties.put("ike_kategoria", ikeResult != null ? ikeResult.getIkeKategoria() : null);
```

- [ ] **Krok 4: Usuń legacy tabele z 01_schema.sql**

Usuń wszystkie bloki `CREATE TABLE` dla:
`placowka`, `strefy_zagrozen`, `miejsca_relokacji`, `zasob_transportu`,
`drogi_ewakuacyjne`, `biale_plamy`, `ike_results`, `evacuation_decisions`

wraz z ich `CREATE INDEX` blokami i sekcjami komentarzy.

- [ ] **Krok 5: Zaktualizuj 03_seed_layers.sql — usuń warstwy L-01 do L-07**

W `03_seed_layers.sql` usuń wszystkie wiersze INSERT dla warstw L-01, L-02, L-03, L-04,
L-05, L-06, L-07. Zachowaj tylko L-08, L-09, L-10.

Wstaw nowe warstwy dla entity registry (zastępują L-01):

```sql
INSERT INTO layer_config (id, nazwa, komponent, typ_geometrii, domyslnie_wlaczona, endpoint, opis)
VALUES
  ('L-01', 'Jednostki ochrony ludności', 'EntityLayer', 'Point', true,  '/api/layers/L-01', 'Wszystkie jednostki z entity_registry'),
  ('L-02', 'Alerty zagrożeń',            'ThreatAlertLayer', 'Point', true, '/api/threats/active', 'Aktywne alerty z IMGW i manualnych triggerów')
ON CONFLICT (id) DO UPDATE SET nazwa = EXCLUDED.nazwa, endpoint = EXCLUDED.endpoint;
```

- [ ] **Krok 6: Zweryfikuj kompilację backendu**

```bash
cd backend
./mvnw compile -q
```

Oczekiwane: `BUILD SUCCESS`, zero błędów.

Jeśli są błędy kompilacji: szukaj pozostałych importów usuniętych klas przez
`grep -r "IkeResult\|Placowka\|StrefaZagrozen\|MiejsceRelokacji\|ZasobTransportu" src/`
i usuń.

- [ ] **Krok 7: Commit**

```
feat(R2.1): backend legacy removal — IKE, Placowka, StrefaZagrozen, MiejsceRelokacji, ZasobTransportu
```

---

## Task 7: REVISION 2 — R2.2 Frontend legacy removal

**Files:**
- Delete: `frontend/src/components/map/layers/ThreatZoneLayer.tsx`
- Modify: `frontend/src/components/map/MapContainer.tsx`
- Modify: `frontend/src/components/map/layers/EntityLayer.tsx`
- Modify: `frontend/src/components/map/EntityPopup.tsx`
- Modify: `frontend/src/types/gis.ts`

- [ ] **Krok 1: Usuń ThreatZoneLayer.tsx**

```bash
rm frontend/src/components/map/layers/ThreatZoneLayer.tsx
```

- [ ] **Krok 2: Zastąp MapContainer.tsx**

```tsx
import { MapContainer as LeafletMapContainer, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import AdminBoundaryLayer from './layers/AdminBoundaryLayer'
import EntityLayer from './layers/EntityLayer'

const MAP_CENTER: [number, number] = [52.1, 19.4]
const INITIAL_ZOOM = 6
const MAP_MIN_ZOOM = 5
const MAP_MAX_BOUNDS: [[number, number], [number, number]] = [[48.0, 13.0], [55.5, 26.5]]

function MapContainer() {
  return (
    <LeafletMapContainer
      center={MAP_CENTER}
      zoom={INITIAL_ZOOM}
      minZoom={MAP_MIN_ZOOM}
      maxBounds={MAP_MAX_BOUNDS}
      maxBoundsViscosity={1.0}
      style={{ height: '100%', width: '100%' }}
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      />
      <AdminBoundaryLayer />
      <EntityLayer />
    </LeafletMapContainer>
  )
}

export default MapContainer
```

- [ ] **Krok 3: Zastąp EntityLayer.tsx — usuń IKE coloring**

```tsx
import { CircleMarker, Popup } from 'react-leaflet'
import EntityPopup from '../EntityPopup'
import { useEntityLayerData } from '../../../hooks/useEntityLayerData'
import type { EntityFeatureProperties } from '../../../types/gis'
import { useMapStore } from '../../../store/mapStore'

const CATEGORY_COLORS: Record<string, string> = {
  social_care_dps: '#2563EB',
  prm_unit: '#DC2626',
  prm_cooperating_unit: '#7C3AED',
  state_forest_unit: '#15803D',
  water_management_unit: '#0891B2',
  hospital_public: '#EA580C',
}

const FALLBACK_COLOR = '#6B7280'

function EntityLayer() {
  const isVisible = useMapStore((state) => state.activeLayers['L-01'] ?? true)
  const categoryFilters = useMapStore((state) => state.entityCategoryFilters)
  const selectedCategories = Object.entries(categoryFilters)
    .filter(([, enabled]) => enabled)
    .map(([code]) => code)
  const { data } = useEntityLayerData(selectedCategories)

  if (!isVisible || !data?.features) return null

  return data.features.map((feature) => {
    const [lng, lat] = feature.geometry.coordinates as [number, number]
    const props = feature.properties as EntityFeatureProperties
    const color = CATEGORY_COLORS[props.category_code] ?? FALLBACK_COLOR

    return (
      <CircleMarker
        key={props.id}
        center={[lat, lng]}
        radius={8}
        pane="markerPane"
        pathOptions={{
          color,
          fillColor: color,
          fillOpacity: 0.85,
          weight: 2,
        }}
      >
        <Popup maxWidth={320} minWidth={250}>
          <EntityPopup properties={props} />
        </Popup>
      </CircleMarker>
    )
  })
}

export default EntityLayer
```

- [ ] **Krok 4: Zastąp EntityPopup.tsx — usuń sekcję IKE**

```tsx
import type { CSSProperties } from 'react'
import type { EntityFeatureProperties } from '../../types/gis'

interface EntityPopupProps {
  properties: EntityFeatureProperties
}

function EntityPopup({ properties }: EntityPopupProps) {
  const residents = asNumber(properties.attributes['liczba_podopiecznych'])
  const capacity = asNumber(properties.attributes['pojemnosc_ogolna'])
  const dependence = asNumber(properties.attributes['niesamodzielni_procent'])
  const dependencePct = dependence != null ? Math.round(dependence * 100) : null

  return (
    <div style={{ minWidth: 260, fontSize: 14, lineHeight: 1.5 }}>
      <div style={{ fontWeight: 700, marginBottom: 4 }}>{properties.name}</div>
      <div style={{ color: '#6B7280', marginBottom: 8, fontSize: 12 }}>
        {properties.category_name}
        {properties.subtitle ? ` · ${properties.subtitle}` : ''}
      </div>

      <hr style={dividerStyle} />

      <div style={rowStyle}>
        <span>Zrodlo</span>
        <strong>{properties.source_name}</strong>
      </div>
      <div style={rowStyle}>
        <span>Status</span>
        <strong>{properties.status ?? 'brak'}</strong>
      </div>
      {properties.owner_name && (
        <div style={rowStyle}>
          <span>Operator</span>
          <strong>{properties.owner_name}</strong>
        </div>
      )}
      {properties.address_raw && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ color: '#6B7280', fontSize: 12 }}>Adres</div>
          <div>{properties.address_raw}</div>
        </div>
      )}

      {(residents != null || capacity != null || dependencePct != null) && (
        <>
          <hr style={dividerStyle} />
          {residents != null && (
            <div style={rowStyle}>
              <span>Osoby / podopieczni</span>
              <strong>{residents}</strong>
            </div>
          )}
          {capacity != null && (
            <div style={rowStyle}>
              <span>Pojemnosc</span>
              <strong>{capacity}</strong>
            </div>
          )}
          {dependencePct != null && (
            <div style={rowStyle}>
              <span>Niesamodzielni</span>
              <strong>{dependencePct}%</strong>
            </div>
          )}
        </>
      )}

      {(properties.contact_phone || properties.contact_email || properties.www) && (
        <>
          <hr style={dividerStyle} />
          {properties.contact_phone && <div style={rowStyle}><span>Telefon</span><strong>{properties.contact_phone}</strong></div>}
          {properties.contact_email && <div style={rowStyle}><span>Email</span><strong>{properties.contact_email}</strong></div>}
          {properties.www && (
            <div style={{ marginBottom: 8 }}>
              <a href={properties.www} target="_blank" rel="noreferrer" style={linkStyle}>
                Otworz strone
              </a>
            </div>
          )}
        </>
      )}

      <hr style={dividerStyle} />

      <div style={{ color: '#6B7280', fontSize: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span>Ostatni import: {properties.last_seen_at ? new Date(properties.last_seen_at).toLocaleString('pl-PL') : 'brak'}</span>
        {properties.match_confidence != null && <span>Pewnosc dopasowania: {Math.round(properties.match_confidence * 100)}%</span>}
        {properties.source_url && (
          <a href={properties.source_url} target="_blank" rel="noreferrer" style={linkStyle}>
            Rekord zrodlowy
          </a>
        )}
      </div>
    </div>
  )
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' ? value : null
}

const dividerStyle: CSSProperties = { border: 'none', borderTop: '1px solid #374151', margin: '8px 0' }
const rowStyle: CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 4 }
const linkStyle: CSSProperties = { color: '#60A5FA', textDecoration: 'none' }

export default EntityPopup
```

- [ ] **Krok 5: Zastąp gis.ts — usuń IkeCategory i ThreatZoneProperties**

```typescript
export interface GeoJsonGeometry {
  type: string
  coordinates: number[] | number[][] | number[][][]
}

export interface GeoJsonFeature<P = Record<string, unknown>> {
  type: 'Feature'
  geometry: GeoJsonGeometry
  properties: P
}

export interface GeoJsonCollection<P = Record<string, unknown>> {
  type: 'FeatureCollection'
  features: GeoJsonFeature<P>[]
  layer_id?: string
  ostatnia_aktualizacja?: string
  feature_count?: number
}

export interface EntityFeatureProperties {
  id: number
  source_record_id: string
  name: string
  subtitle: string | null
  category_code: string
  category_name: string
  category_icon: string | null
  source_code: string
  source_name: string
  status: string | null
  owner_name: string | null
  address_raw: string | null
  teryt_woj: string | null
  teryt_pow: string | null
  teryt_gmina: string | null
  lat: number | null
  lon: number | null
  contact_phone: string | null
  contact_email: string | null
  www: string | null
  source_url: string | null
  last_seen_at: string | null
  last_import_batch_id: number | null
  source_priority: number | null
  match_confidence: number | null
  attributes: Record<string, unknown>
}

export interface EntityCategory {
  code: string
  name: string
  act_ref: string | null
  icon: string | null
  default_layer_group: string | null
  geometry_mode: string | null
  entity_count: number
}

export interface EntitySummaryBucket {
  code: string
  name: string
  count: number
}

export interface EntitySummary {
  kod_teryt: string
  total_entities: number
  verified_entities: number
  needs_review_entities: number
  categories: EntitySummaryBucket[]
  sources: EntitySummaryBucket[]
}
```

- [ ] **Krok 6: Zweryfikuj build frontendu**

```bash
cd frontend
npm run build
```

Oczekiwane: `✓ built in Xs`, zero błędów TypeScript.

- [ ] **Krok 7: Commit**

```
feat(R2.2): frontend legacy removal — ThreatZoneLayer, IKE coloring, IKE popup, IkeCategory type
```

---

## Task 8: REVISION 2 — R2.3 Update BACKLOG.md

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Krok 1: Zaktualizuj sekcję "Aktywne zadanie"**

```markdown
**Aktywne:** `REVISION 2 — R2.1 Backend legacy removal`
```

- [ ] **Krok 2: Wstaw REVISION 2 przed istniejącym REVISION 1**

Wstaw po wierszu `### ✅ 1.12` następujące zadania:

```markdown
---

## REVISION 2 — Usunięcie legacy

> Wykonuj w kolejności R2.1 → R2.2 → R2.3. Każde zadanie kończy się działającą aplikacją.
> R2.1 i R2.2 zaimplementowane przez plan docs/superpowers/plans/2026-04-22-concept-redesign.md.

### ✅ R2.1 — Backend legacy removal

Patrz: Task 6 w planie implementacji.
Weryfikacja: `./mvnw compile -q` → BUILD SUCCESS.
Commit: `feat(R2.1): backend legacy removal`

---

### ✅ R2.2 — Frontend legacy removal

Patrz: Task 7 w planie implementacji.
Weryfikacja: `npm run build` → zero błędów TypeScript.
Commit: `feat(R2.2): frontend legacy removal`

---

### ⬜ R2.3 — Docs update

Patrz: Tasks 1–5 w planie implementacji.
Weryfikacja: sprawdź że słowo "IKE" nie występuje w PRD.md, ARCHITEKTURA_PLAN.md, CLAUDE.md.
```bash
grep -r "IKE\|ike_score\|ike_kategoria\|FloodImportAgent\|DecisionAgent\|evacuation_decisions\|strefy_zagrozen" docs/ CLAUDE.md
# oczekiwane: brak wyników
```
Commit: `docs(R2.3): docs update — nowy cel, nowi agenci, nowe tabele`

---
```

- [ ] **Krok 3: Zaktualizuj istniejący REVISION 1**

Dodaj do istniejącego REVISION 1:

```markdown
### ⬜ REVISION 1 — UX fixes

(istniejące punkty zachowaj + dodaj:)

**Dodatkowe zadania (nowe):**

* Fix layer selection: wiele warstw może być podświetlonych naraz. Zmień `selectedRegion`
  (jeden na cały store) na `selectedFeatureByLayer: Record<'L-08'|'L-09'|'L-10', string|null>`.
  Zaznaczenie w warstwie X czyści zaznaczenie w pozostałych warstwach.
  Patrz: Task 9 w planie implementacji.

Weryfikacja:
```
☐ Kliknięcie województwa → podświetlone tylko województwo
☐ Następnie kliknięcie powiatu → podświetlony powiat, województwo traci podświetlenie
☐ npm run build → 0 błędów TypeScript
```

Commit: `feat(R1): UX fixes — layer selection per warstwa + ...`
```

- [ ] **Krok 4: Dodaj zadanie DT-LOGS-TESTS przed iteracją v1.1**

```markdown
---

## DŁUG TECHNICZNY — Logi i testy

### ⬜ DT-LOGS-TESTS — Logi + testy dla istniejących serwisów

**Pliki do modyfikacji:**
- `backend/.../agent/AdminBoundaryImportAgent.java`
- `backend/.../service/EntityImportService.java`
- `backend/.../service/EntityRegistryService.java`
- `backend/.../service/AdminBoundaryService.java`
- `backend/.../service/GeoService.java` (już ma Logger — dodaj logi do key metod)

**Pliki do stworzenia (testy):**
- `backend/src/test/java/pl/lublin/dashboard/service/EntityRegistryServiceTest.java`
- `backend/src/test/java/pl/lublin/dashboard/service/AdminBoundaryServiceTest.java`
- `backend/src/test/java/pl/lublin/dashboard/agent/AdminBoundaryImportAgentTest.java`

**Wzorzec logowania (jednolity dla wszystkich):**
```java
log.info("[AdminBoundaryImportAgent] import started — poziomy={}", Arrays.toString(poziomy));
log.info("[AdminBoundaryImportAgent] import completed — inserted={}", count);
log.error("[AdminBoundaryImportAgent] import failed — {}", e.getMessage());
```

**Wzorzec testu (EntityRegistryServiceTest jako przykład):**
```java
@ExtendWith(MockitoExtension.class)
class EntityRegistryServiceTest {

    @Mock EntityRegistryEntryRepository entityRepository;
    @Mock EntityCategoryRepository categoryRepository;
    @Mock EntitySourceRepository sourceRepository;
    @Mock GranicaAdministracyjnaRepository granicaRepository;
    @Mock ObjectMapper objectMapper;

    @InjectMocks EntityRegistryService service;

    @Test
    void filterEntries_byKodWoj_returnsOnlyMatchingEntries() {
        EntityRegistryEntry match = new EntityRegistryEntry();
        match.setTerytWoj("06");
        EntityRegistryEntry noMatch = new EntityRegistryEntry();
        noMatch.setTerytWoj("14");
        when(entityRepository.findAll()).thenReturn(List.of(match, noMatch));

        Map<String, Object> result = service.getEntities(null, null, "06", null, null, null, null);

        assertThat((Integer) result.get("count")).isEqualTo(1);
    }

    @Test
    void filterEntries_emptyKodWoj_returnsAllEntries() {
        when(entityRepository.findAll()).thenReturn(List.of(new EntityRegistryEntry(), new EntityRegistryEntry()));
        Map<String, Object> result = service.getEntities(null, null, null, null, null, null, null);
        assertThat((Integer) result.get("count")).isEqualTo(2);
    }
}
```

**Weryfikacja:**
```bash
cd backend
./mvnw test -pl . -Dtest="EntityRegistryServiceTest,AdminBoundaryServiceTest,AdminBoundaryImportAgentTest" -q
# oczekiwane: BUILD SUCCESS, testy: passed
```

**Commit:** `test(DT): logi + testy jednostkowe — EntityRegistryService, AdminBoundaryService, AdminBoundaryImportAgent`

---


- [ ] **Krok 5: Zastąp iterację v1.1 nowymi zadaniami**

Usuń całą starą sekcję `## Iteracja v1.1 — Event-driven core` (zadania 2.1–2.8).

Wstaw:

```markdown
## Iteracja v1.1 — Zasoby + Alerty zagrożeń

Cel: operator widzi zasoby każdej jednostki na mapie i może filtrować po typie zasobu.
Alert z IMGW (poziom wody > próg) automatycznie podświetla jednostki w zasięgu.

Deliverable: `GET /api/resource-types` zwraca typy zasobów; kliknięcie jednostki
pokazuje jej zasoby w popupie; aktywny alert z IMGW podświetla jednostki w zasięgu
w ciągu 30 sekund bez odświeżenia strony.
```
---

### ⬜ 2.1 — SQL: resource_type + entity_resources + threat_alert

**Pliki do stworzenia/modyfikacji:**
- `backend/src/main/resources/db/01_schema.sql` (dodaj 3 tabele)
- `backend/src/main/java/pl/lublin/dashboard/model/ResourceType.java`
- `backend/src/main/java/pl/lublin/dashboard/model/EntityResource.java`
- `backend/src/main/java/pl/lublin/dashboard/model/ThreatAlert.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/ResourceTypeRepository.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/EntityResourceRepository.java`
- `backend/src/main/java/pl/lublin/dashboard/repository/ThreatAlertRepository.java`

**Dokumenty referencyjne:** `docs/DATA_SCHEMA.md` (nowe tabele z Task 4 planu)

**Opis:**
Dodaj do `01_schema.sql` DDL dla `resource_type`, `entity_resources`, `threat_alert`
dokładnie jak w `docs/DATA_SCHEMA.md`.

Encje JPA — przykład dla `EntityResource`:
```java
@Entity
@Table(name = "entity_resources")
public class EntityResource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "resource_type_code", nullable = false)
    private String resourceTypeCode;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "is_available")
    private Boolean isAvailable = true;

    @Column(name = "last_updated")
    private OffsetDateTime lastUpdated;

    private String source;
    // getters/setters
}
```

**Weryfikacja:**
```bash
./mvnw compile -q
# BUILD SUCCESS

docker compose up -d postgres
sleep 10
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "\dt" | grep -E "resource_type|entity_resources|threat_alert"
# oczekiwane: 3 tabele widoczne
```

**Commit:** `feat(2.1): SQL schema — resource_type, entity_resources, threat_alert`

---

### ⬜ 2.2 — Seed: typy zasobów + mockowane zasoby dla istniejących jednostek

**Pliki do stworzenia:**
- `backend/src/main/resources/db/07_seed_resource_types.sql`
- `backend/src/main/resources/db/08_seed_entity_resources.sql`

**Opis:**
`07_seed_resource_types.sql` — ~50 typów zasobów w kategoriach:

```sql
INSERT INTO resource_type (code, name, category, unit_of_measure) VALUES
  ('woz_cysternowy',         'Wóz cysternowy GBA',           'pojazd_gasniczy', 'szt'),
  ('woz_drabinowy',          'Wóz drabinowy',                'pojazd_gasniczy', 'szt'),
  ('woz_ratownictwa',        'Wóz ratownictwa technicznego', 'pojazd_ratowniczy', 'szt'),
  ('ponton_motorowy',        'Ponton motorowy',              'sprzet_wodny', 'szt'),
  ('ponton_wiosłowy',        'Ponton wiosłowy',              'sprzet_wodny', 'szt'),
  ('pompa_szlamowa',         'Pompa szlamowa',               'sprzet_wodny', 'szt'),
  ('agregat_pradotworczy',   'Agregat prądotwórczy',         'energetyczny', 'szt'),
  ('oswietlenie_polowe',     'Oświetlenie polowe',           'energetyczny', 'kpl'),
  ('nosze_transportowe',     'Nosze transportowe',           'medyczny', 'szt'),
  ('defibrylator_aed',       'Defibrylator AED',             'medyczny', 'szt'),
  ('respirator',             'Respirator transportowy',      'medyczny', 'szt'),
  ('ambulans_type_c',        'Ambulans typu C',              'pojazd_medyczny', 'szt'),
  ('ambulans_type_b',        'Ambulans typu B',              'pojazd_medyczny', 'szt'),
  ('namiot_polowy',          'Namiot polowy medyczny',       'logistyczny', 'szt'),
  ('generator_wody',         'Generator wody pitnej',        'logistyczny', 'szt')
  -- ... dodaj do ~50 wierszy
ON CONFLICT (code) DO NOTHING;
```

`08_seed_entity_resources.sql` — mockowane zasoby dla pierwszych 20 jednostek z entity_registry:

```sql
INSERT INTO entity_resources (entity_id, resource_type_code, quantity, is_available, source)
SELECT e.id, rt.code, floor(random() * 3 + 1)::int, true, 'mock'
FROM entity_registry e
CROSS JOIN resource_type rt
WHERE e.id <= 20
  AND rt.code IN ('woz_cysternowy', 'ponton_motorowy', 'agregat_pradotworczy', 'nosze_transportowe')
ON CONFLICT (entity_id, resource_type_code) DO NOTHING;
```

**Weryfikacja:**
```bash
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM resource_type;"
# oczekiwane: >= 15

docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM entity_resources;"
# oczekiwane: > 0
```

**Commit:** `feat(2.2): seed resource_type (50 typów) + mockowane entity_resources`

---

### ⬜ 2.3 — ThreatAlertImportAgent + ThreatAlertEvent + endpoint manual

**Pliki do stworzenia:**
- `backend/.../event/ThreatAlertEvent.java`
- `backend/.../event/NearbyUnitsComputedEvent.java`
- `backend/.../config/AsyncConfig.java`
- `backend/.../agent/ThreatAlertImportAgent.java`
- `backend/.../controller/ThreatController.java`

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.1–4.3)

**Opis:**

`ThreatAlertEvent.java`:
```java
public class ThreatAlertEvent extends ApplicationEvent {
    private final Long alertId;
    private final String threatType;
    private final String level;
    private final String sourceApi;
    private final Double lat;
    private final Double lon;
    private final Double radiusKm;
    private final String correlationId;
    // konstruktor + gettery
}
```

`ThreatAlertImportAgent` — polling IMGW:
```java
@Service
@Slf4j
public class ThreatAlertImportAgent {
    // URL IMGW hydro API
    private static final String IMGW_HYDRO_URL = "https://danepubliczne.imgw.pl/api/data/hydro";
    // Progi: stan ostrzegawczy (pole "stan_wody_data_pomiaru" + "stan_wody")
    // IMGW zwraca tablicę obiektów: [{id_stacji, stacja, rzeka, stan_wody, ...}]
    // Mapa stacji do lokalizacji dostępna z: https://hydro.imgw.pl/ (lub hardkodowana dla P1)
}
```

`ThreatController` endpointy:
- `GET /api/threats/active` → lista aktywnych alertów
- `POST /api/threats/manual` → ręczny trigger
- `POST /api/threats/{id}/deactivate` → deaktywacja alertu

IMGW API format odpowiedzi (jeden obiekt stacji):
```json
{
  "id_stacji": "150180180",
  "stacja": "Lublin",
  "rzeka": "Bystrzyca",
  "województwo": "lubelskie",
  "stan_wody": "245",
  "stan_wody_data_pomiaru": "2026-04-22 06:00"
}
```
Uwaga: IMGW nie zwraca koordynat stacji w tym endpoincie. Dla P1 użyj mockowanej mapy
`Map<String, double[]> STATION_COORDS` z kilkoma stacjami hardkodowanymi. Geokodowanie
stacji z API IMGW to zadanie v1.2.

**Weryfikacja:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
sleep 8

curl -s http://localhost:8080/api/threats/active | jq '.count'
# oczekiwane: 0 (brak alertów na start)

curl -s -X POST http://localhost:8080/api/threats/manual \
  -H "Content-Type: application/json" \
  -d '{"threat_type":"flood","level":"warning","lat":51.2,"lon":22.5,"radius_km":25.0}' \
  | jq '.status'
# oczekiwane: "started"

sleep 3
curl -s http://localhost:8080/api/threats/active | jq '.count'
# oczekiwane: 1
```

**Commit:** `feat(2.3): ThreatAlertEvent + ThreatAlertImportAgent (IMGW polling) + ThreatController`

---

### ⬜ 2.4 — NearbyUnitsAgent + NearbyUnitsComputedEvent

**Pliki do stworzenia:**
- `backend/.../agent/NearbyUnitsAgent.java`
- `backend/.../repository/EntityResourceRepository.java` (jeśli nie w 2.1)

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.4)

**Opis:**

```java
@Service
@Slf4j
public class NearbyUnitsAgent {

    @Autowired private EntityRegistryEntryRepository entityRepository;
    @Autowired private ApplicationEventPublisher publisher;

    @EventListener
    @Async("agentTaskExecutor")
    public void onThreatAlert(ThreatAlertEvent event) {
        log.info("[NearbyUnitsAgent] processing alert — alertId={}, correlationId={}",
                event.getAlertId(), event.getCorrelationId());

        // Zapytanie PostGIS przez JPQL lub native query:
        // SELECT e FROM EntityRegistryEntry e
        // WHERE function('ST_DWithin',
        //   function('cast', e.geom, 'geography'),
        //   function('ST_MakePoint', :lon, :lat)::geography,
        //   :radiusMeters) = true
        // Lub użyj @Query z native SQL:
        // SELECT id FROM entity_registry
        // WHERE ST_DWithin(geom::geography, ST_MakePoint(:lon,:lat)::geography, :radiusMeters)

        List<Long> entityIds = findNearbyEntityIds(event.getLat(), event.getLon(),
                event.getRadiusKm() * 1000);

        log.info("[NearbyUnitsAgent] found {} units near alert={}", entityIds.size(), event.getAlertId());

        publisher.publishEvent(new NearbyUnitsComputedEvent(
                this, event.getCorrelationId(), entityIds, String.valueOf(event.getAlertId())));
    }
}
```

Dodaj native query do `EntityResourceRepository`:
```java
@Query(value = """
    SELECT e.id FROM entity_registry e
    WHERE ST_DWithin(e.geom::geography,
                     ST_MakePoint(:lon, :lat)::geography,
                     :radiusMeters)
    """, nativeQuery = true)
List<Long> findEntityIdsWithinRadius(
    @Param("lat") double lat,
    @Param("lon") double lon,
    @Param("radiusMeters") double radiusMeters
);
```

**Weryfikacja:**
```bash
# Utwórz alert manualny + sprawdź czy NearbyUnitsAgent zadziałał
curl -s -X POST http://localhost:8080/api/threats/manual \
  -H "Content-Type: application/json" \
  -d '{"threat_type":"flood","level":"alarm","lat":51.247,"lon":22.568,"radius_km":50.0}'

sleep 5

# Sprawdź logi backendu — powinny zawierać:
# "[NearbyUnitsAgent] found X units near alert=1"
```

**Commit:** `feat(2.4): NearbyUnitsAgent — ST_DWithin spatial query + NearbyUnitsComputedEvent`

---

### ⬜ 2.5 — LiveFeedService + WebSocket config

**Pliki do stworzenia:**
- `backend/.../service/LiveFeedService.java`
- `backend/.../config/WebSocketConfig.java` (jeśli nie istnieje — plik już jest)

**Dokumenty referencyjne:** `docs/ARCHITEKTURA_PLAN.md` (§4.5), `docs/API_REFERENCE.md` (WebSocket)

**Opis:**
```java
@Service
@Slf4j
public class LiveFeedService {

    @Autowired private SimpMessagingTemplate messagingTemplate;

    @EventListener
    @Async("agentTaskExecutor")
    public void onThreatAlert(ThreatAlertEvent event) {
        log.info("[LiveFeedService] push threat-alerts — correlationId={}", event.getCorrelationId());
        // pobierz alert z bazy, zbuduj DTO, push
        messagingTemplate.convertAndSend("/topic/threat-alerts",
                Map.of("alertId", event.getAlertId(), "type", event.getThreatType(),
                       "level", event.getLevel(), "lat", event.getLat(), "lon", event.getLon(),
                       "radiusKm", event.getRadiusKm()));
    }

    @EventListener
    @Async("agentTaskExecutor")
    public void onNearbyUnitsComputed(NearbyUnitsComputedEvent event) {
        log.info("[LiveFeedService] push nearby-units — count={}, correlationId={}",
                event.getEntityIds().size(), event.getCorrelationId());
        messagingTemplate.convertAndSend("/topic/nearby-units",
                Map.of("alertId", event.getAlertId(),
                       "entityIds", event.getEntityIds(),
                       "correlationId", event.getCorrelationId()));
    }
}
```

**Weryfikacja:**
```bash
# Zainstaluj wscat: npm install -g wscat
wscat -c ws://localhost:8080/ws
# W osobnym terminalu wyślij manual alert
curl -s -X POST http://localhost:8080/api/threats/manual \
  -H "Content-Type: application/json" \
  -d '{"threat_type":"flood","level":"alarm","lat":51.247,"lon":22.568,"radius_km":50.0}'
# W konsoli wscat po subskrypcji /topic/threat-alerts powinien pojawić się push
```

**Commit:** `feat(2.5): LiveFeedService — STOMP push po ThreatAlertEvent + NearbyUnitsComputedEvent`

---

### ⬜ 2.6 — Frontend: ThreatAlertLayer

**Pliki do stworzenia:**
- `frontend/src/components/map/layers/ThreatAlertLayer.tsx`
- `frontend/src/hooks/useThreatAlerts.ts`

**Opis:**
`useThreatAlerts` — React Query `GET /api/threats/active`, staleTime 30_000.
`ThreatAlertLayer` — `CircleMarker` dla każdego alertu, kolor wg level:
- `warning`: `#F59E0B` (żółty)
- `alarm`: `#EF4444` (czerwony)
- `emergency`: `#7C3AED` (fioletowy)
Radius markera ≈ `radius_km * 1.5` px (przybliżenie wizualne, nie geograficzne).
Dodaj komponent do `MapContainer.tsx`.

**Weryfikacja:**
```
Manualne — przeglądarka:
☐ POST /api/threats/manual → marker alertu pojawia się na mapie
☐ Kolor zależny od level
☐ npm run build → 0 błędów TypeScript
```

**Commit:** `feat(2.6): ThreatAlertLayer — wizualizacja alertów zagrożeń na mapie`

---

### ⬜ 2.7 — Frontend: zasoby w EntityPopup + resource filter

**Pliki do modyfikacji:**
- `frontend/src/components/map/EntityPopup.tsx`
- `frontend/src/components/panels/EntityFilterPanel.tsx`
- `frontend/src/hooks/useEntityResources.ts` (nowy)

**Opis:**
`useEntityResources(entityId)` — `GET /api/entity-resources?entityId={id}`, staleTime 60_000.

W `EntityPopup` dodaj sekcję zasobów pod danymi kontaktowymi:
```tsx
const { data: resources } = useEntityResources(properties.id)

// W JSX:
{resources && resources.resources.length > 0 && (
  <>
    <hr style={dividerStyle} />
    <div style={{ fontWeight: 600, marginBottom: 4 }}>Zasoby</div>
    {resources.resources.map(r => (
      <div key={r.resource_type_code} style={rowStyle}>
        <span>{r.name}</span>
        <strong>{r.quantity} {r.is_available ? '✓' : '(niedostępny)'}</strong>
      </div>
    ))}
  </>
)}
```

**Weryfikacja:**
```
Manualne:
☐ Kliknięcie jednostki z zasobami → sekcja "Zasoby" widoczna w popupie
☐ npm run build → 0 błędów
```

**Commit:** `feat(2.7): zasoby jednostek w EntityPopup + useEntityResources hook`

---

### ⬜ 2.8 — Frontend: NearbyUnitsPanel + WebSocket client

**Pliki do stworzenia:**
- `frontend/src/services/websocketService.ts`
- `frontend/src/hooks/useWebSocket.ts`
- `frontend/src/components/panels/NearbyUnitsPanel.tsx`

**Opis:**
`websocketService.ts` — SockJS + `@stomp/stompjs`, reconnect 5s, subskrypcja `/topic/threat-alerts`
i `/topic/nearby-units`.

`useWebSocket` — hook który po `/topic/nearby-units` aktualizuje Zustand store
(nowe pole `nearbyEntityIds: number[]`, `activeAlertId: string | null`).

`NearbyUnitsPanel` — renderowany gdy `nearbyEntityIds.length > 0`. Lista jednostek
z nazwy + kategorii + liczby dostępnych zasobów. Przycisk "Wyczyść alert" → DELETE /api/threats/{id}.

**Weryfikacja:**
```
Manualne:
☐ POST /api/threats/manual → po ~5s NearbyUnitsPanel pojawia się z listą jednostek
☐ Bez ręcznego odświeżenia strony
☐ npm run build → 0 błędów
```

**Commit:** `feat(2.8): WebSocket client + NearbyUnitsPanel — live feed alertów i pobliskich jednostek`

---

### ⬜ 2.9 — Frontend: manual threat trigger UI

**Pliki do stworzenia:**
- `frontend/src/components/panels/ThreatTriggerPanel.tsx`

**Opis:**
Formularz w panelu bocznym: typ zagrożenia (dropdown), poziom (dropdown), radius (slider 10–100 km),
przycisk "Aktywuj alert". Wywołuje `POST /api/threats/manual`. Spinner podczas czekania.

**Weryfikacja:**
```
Manualne:
☐ ThreatTriggerPanel widoczny w panelu bocznym
☐ Wypełnienie formularza + "Aktywuj" → marker alertu pojawia się na mapie
☐ NearbyUnitsPanel wypełnia się po ~5s
```

**Commit:** `feat(2.9): ThreatTriggerPanel — ręczny trigger alertu zagrożenia`

---


- [ ] **Krok 6: Zastąp iterację v1.2**

Usuń stare zadania `3.1–3.4`. Wstaw:

```markdown
## Iteracja v1.2 — Importy z publicznych API

Cel: rzeczywiste dane jednostek PSP, PRM i podmiotów leczniczych z polskich rejestrów.
Geokodowanie adresów przez Nominatim OSM. Clustering dla > 1000 punktów na mapie.

Deliverable: mapa z > 300 stacjami PSP i > 200 ZRM, clustering działa przy zoom < 9.

---

### ⬜ 3.1 — PSP bulk import (CSV dane.gov.pl + geokodowanie)

**Pliki do stworzenia:**
- `backend/.../agent/PspImportAgent.java`
- `backend/.../service/NominatimGeocoderService.java`
- `backend/.../controller/ImportController.java` (jeśli nie istnieje — rozszerz)

**Źródło:** `https://dane.gov.pl/pl/dataset/1050,dane-teleadresowe-jednostek-organizacyjnych-psp`
Format: CSV. Pobierz przez HTTP, parsuj (Apache Commons CSV lub ręcznie), upsert do `entity_registry`.

**Rate limiting Nominatim:** max 1 req/s (regulamin OSM). Użyj `Thread.sleep(1100)` między zapytaniami.
Geokodowanie tylko dla rekordów bez koordynat.

**Commit:** `feat(3.1): PspImportAgent — import PSP z dane.gov.pl + Nominatim geokodowanie`

---

### ⬜ 3.2 — PRM/ZRM bulk import (XLSX rjwprm.ezdrowie.gov.pl)

**Pliki do stworzenia:**
- `backend/.../agent/PrmImportAgent.java`

**Źródło:** `https://rjwprm.ezdrowie.gov.pl/` — plik XLSX aktualizowany codziennie.
Parsowanie przez Apache POI. Upsert do `entity_registry` z `category_code='prm_unit'`.

**Commit:** `feat(3.2): PrmImportAgent — import ZRM/SOR z RJWPRM XLSX + geokodowanie`

---

### ⬜ 3.3 — RPWDL import (podmioty lecznicze — XLSX ezdrowie.gov.pl)

**Pliki do stworzenia:**
- `backend/.../agent/RpwdlImportAgent.java`

**Źródło:** `https://ezdrowie.gov.pl/portal/home/rejestry-medyczne/dane-z-rejestrow-medycznych`
Szpitale, SPZOZ, hospicja. Parsowanie XLSX przez Apache POI.

**Commit:** `feat(3.3): RpwdlImportAgent — import podmiotów leczniczych z RPWDL`

---

### ⬜ 3.4 — Clustering jednostek na mapie

**Warunek wstępny:** zaimplementowane 3.1 i 3.2 (> 500 punktów w entity_registry).

**Pliki do modyfikacji:**
- `frontend/package.json` (dodaj `supercluster` lub `leaflet.markercluster`)
- `frontend/src/components/map/layers/EntityLayer.tsx`
- `frontend/src/hooks/useEntityLayerData.ts`

**Logika:**
- zoom < 9: grupuj jednostki (supercluster), marker = koło z liczbą
- zoom ≥ 11: pojedyncze markery
- kliknięcie klastra → fitBounds do bbox klastra

**Commit:** `feat(3.4): clustering jednostek — supercluster, zoom-based grouping`

---


- [ ] **Krok 7: Zachowaj iterację v1.3 bez zmian**

Iteracja v1.3 (asystent głosowy + Docker prod) pozostaje niezmieniona.

- [ ] **Krok 8: Zaktualizuj sekcję "Dług techniczny"**

Usuń stare pozycje DT-01, DT-02, DT-03. Zostaw pustą tabelę + notatkę:
```markdown
| # | Opis | Dotyczy | Priorytet |
|---|---|---|---|
| DT-NOMINATIM-CACHE | Cachować wyniki Nominatim w Redis lub prostej tabeli SQL — uniknąć limitu 1 req/s przy dużych importach | 3.1–3.3 | Wysoki |
```

- [ ] **Krok 9: Commit**

docs(BACKLOG): REVISION 2, update REVISION 1, DT-LOGS-TESTS, nowe iteracje v1.1-v1.2


---

## Task 9: REVISION 1 — Fix layer selection per warstwa

**Files:**
- Modify: `frontend/src/store/mapStore.ts`
- Modify: `frontend/src/components/map/layers/AdminBoundaryLayer.tsx`

- [ ] **Krok 1: Zaktualizuj mapStore.ts — dodaj selectedFeatureByLayer**

```typescript
import { create } from 'zustand'

type ActiveLayers = Record<string, boolean>
type EntityCategoryFilters = Record<string, boolean>
type BoundaryLayerId = 'L-08' | 'L-09' | 'L-10'

export type SelectedRegion = {
  name: string
  kod_teryt?: string
  poziom?: string
  properties: Record<string, unknown>
} | null

type MapStore = {
  activeLayers: ActiveLayers
  toggleLayer: (id: string, value: boolean) => void
  entityCategoryFilters: EntityCategoryFilters
  setEntityCategoryFilter: (code: string, value: boolean) => void
  hydrateEntityCategoryFilters: (codes: string[]) => void
  resetEntityCategoryFilters: () => void
  selectedRegion: SelectedRegion
  setSelectedRegion: (region: SelectedRegion) => void
  selectedFeatureByLayer: Record<BoundaryLayerId, string | null>
  setSelectedFeatureForLayer: (layerId: BoundaryLayerId, featureId: string | null) => void
  isPanelCollapsed: boolean
  togglePanel: () => void
}

export const useMapStore = create<MapStore>()((set) => ({
  activeLayers: {
    'L-00': true,
    'L-01': true,
    'L-03': true,
    'L-08': true,
    'L-09': false,
    'L-10': false,
  },
  toggleLayer: (id, value) =>
    set((state) => ({ activeLayers: { ...state.activeLayers, [id]: value } })),
  entityCategoryFilters: {},
  setEntityCategoryFilter: (code, value) =>
    set((state) => ({ entityCategoryFilters: { ...state.entityCategoryFilters, [code]: value } })),
  hydrateEntityCategoryFilters: (codes) =>
    set((state) => {
      const next = { ...state.entityCategoryFilters }
      for (const code of codes) {
        if (!(code in next)) next[code] = true
      }
      return { entityCategoryFilters: next }
    }),
  resetEntityCategoryFilters: () =>
    set((state) => ({
      entityCategoryFilters: Object.fromEntries(
        Object.keys(state.entityCategoryFilters).map((code) => [code, true])
      ),
    })),
  selectedRegion: null,
  setSelectedRegion: (region) => set({ selectedRegion: region }),
  selectedFeatureByLayer: { 'L-08': null, 'L-09': null, 'L-10': null },
  setSelectedFeatureForLayer: (layerId, featureId) =>
    set(() => ({
      selectedFeatureByLayer: { 'L-08': null, 'L-09': null, 'L-10': null, [layerId]: featureId },
    })),
  isPanelCollapsed: false,
  togglePanel: () => set((state) => ({ isPanelCollapsed: !state.isPanelCollapsed })),
}))
```

- [ ] **Krok 2: Zaktualizuj AdminBoundaryLayer.tsx — per-layer selection z cross-layer reset**

```tsx
import { useEffect, useRef } from 'react'
import { GeoJSON } from 'react-leaflet'
import type { Layer } from 'leaflet'
import { useAdminBoundaries } from '../../../hooks/useAdminBoundaries'
import { useMapStore } from '../../../store/mapStore'

const SELECTED_STYLE = { color: '#60A5FA', weight: 2.5, fillOpacity: 0.15 }

const LAYER_STYLES = {
  'L-08': { color: '#6366F1', weight: 2, fillOpacity: 0.04 },
  'L-09': { color: '#4B5563', weight: 1, fillOpacity: 0.03 },
  'L-10': { color: '#374151', weight: 0.5, fillOpacity: 0.02 },
}

type BoundaryLayerId = 'L-08' | 'L-09' | 'L-10'

interface SingleBoundaryLayerProps {
  layerId: BoundaryLayerId
  kodWoj?: string
}

function SingleBoundaryLayer({ layerId, kodWoj }: SingleBoundaryLayerProps) {
  const isVisible = useMapStore((state) => state.activeLayers[layerId] ?? false)
  const setSelectedRegion = useMapStore((state) => state.setSelectedRegion)
  const setSelectedFeatureForLayer = useMapStore((state) => state.setSelectedFeatureForLayer)
  const selectedFeatureId = useMapStore((state) => state.selectedFeatureByLayer[layerId])
  const { data } = useAdminBoundaries(layerId, kodWoj, isVisible)
  const selectedLayerRef = useRef<Layer | null>(null)
  const defaultStyle = LAYER_STYLES[layerId]

  // Gdy inny SingleBoundaryLayer wyczyścił nasz selectedFeatureId — resetuj styl
  useEffect(() => {
    if (selectedFeatureId === null && selectedLayerRef.current) {
      ;(selectedLayerRef.current as unknown as { setStyle: (s: object) => void }).setStyle(defaultStyle)
      selectedLayerRef.current = null
    }
  }, [selectedFeatureId, defaultStyle])

  if (!isVisible || !data?.features?.length) return null

  const onEachFeature = (feature: unknown, layer: Layer) => {
    layer.on('click', () => {
      // reset own previous selection
      if (selectedLayerRef.current && selectedLayerRef.current !== layer) {
        ;(selectedLayerRef.current as unknown as { setStyle: (s: object) => void }).setStyle(defaultStyle)
      }
      ;(layer as unknown as { setStyle: (s: object) => void }).setStyle(SELECTED_STYLE)
      selectedLayerRef.current = layer

      const props = (feature as { properties?: Record<string, unknown> })?.properties ?? {}
      const name = String(props['nazwa'] ?? props['NAME_2'] ?? props['NAZWA'] ?? '')
      const kod_teryt = String(props['kod_teryt'] ?? '')
      const poziom = String(props['poziom'] ?? '')

      // setSelectedFeatureForLayer czyści selectedFeatureByLayer dla pozostałych warstw
      // → ich useEffect wykona reset stylu
      setSelectedFeatureForLayer(layerId, kod_teryt)
      setSelectedRegion({ name, kod_teryt, poziom, properties: props })
    })
  }

  return (
    <GeoJSON
      key={`${layerId}-${data.feature_count}`}
      data={data as unknown as GeoJSON.GeoJsonObject}
      style={defaultStyle}
      onEachFeature={onEachFeature as never}
    />
  )
}

function AdminBoundaryLayer() {
  const { selectedRegion } = useMapStore()

  return (
    <>
      <SingleBoundaryLayer layerId="L-08" />
      <SingleBoundaryLayer layerId="L-09" />
      <SingleBoundaryLayer layerId="L-10" kodWoj={selectedRegion?.kod_teryt?.substring(0, 2)} />
    </>
  )
}

export default AdminBoundaryLayer
```

- [ ] **Krok 3: Zweryfikuj build**

```bash
cd frontend
npm run build
```

Oczekiwane: 0 błędów TypeScript.

- [ ] **Krok 4: Test manualny**

```
☐ Kliknięcie województwa → tylko to województwo podświetlone
☐ Następnie kliknięcie powiatu → powiat podświetlony, województwo traci podświetlenie
☐ Kliknięcie gminy → gmina podświetlona, powiat traci podświetlenie
```

- [ ] **Krok 5: Commit**

```
feat(R1): fix layer selection — selectedFeatureByLayer, cross-layer style reset
```

---

## Self-Review

*Przeprowadzony po napisaniu planu.*

**Spec coverage:**
- ✅ Legacy removal scope (Task 6, 7)
- ✅ CLAUDE.md nowy cel + reguły logowania (Task 1)
- ✅ PRD nowy cel + zakres (Task 2)
- ✅ ARCHITEKTURA_PLAN nowi agenci + eventy (Task 3)
- ✅ DATA_SCHEMA nowe tabele (Task 4)
- ✅ API_REFERENCE nowe endpointy (Task 5)
- ✅ BACKLOG REVISION 2 zadania (Task 8)
- ✅ BACKLOG REVISION 1 extension (Task 8)
- ✅ BACKLOG DT-LOGS-TESTS (Task 8)
- ✅ BACKLOG v1.1 zadania 2.1–2.9 (Task 8)
- ✅ BACKLOG v1.2 zadania 3.1–3.4 (Task 8)
- ✅ Layer selection UX fix (Task 9)
- ✅ Viewport dla całej Polski — już zaimplementowany w MapContainer.tsx, bez zmian

**Brak placeholderów:** sprawdzone — wszystkie kroki mają konkretny kod lub komendy.

**Spójność typów:**
- `BoundaryLayerId = 'L-08' | 'L-09' | 'L-10'` — używany w mapStore.ts i AdminBoundaryLayer.tsx ✅
- `selectedFeatureByLayer` / `setSelectedFeatureForLayer` — spójne w obu plikach ✅
- `ThreatAlertEvent` / `NearbyUnitsComputedEvent` — nazwy spójne między agentami i LiveFeedService ✅
