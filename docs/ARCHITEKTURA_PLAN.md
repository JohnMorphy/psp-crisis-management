# ARCHITEKTURA_PLAN.md — Inteligentna Mapa Województwa Lubelskiego

> Wersja: 1.3 · Data: 2026-04-14

---

## 1. Decyzje architektoniczne

### Stack technologiczny

| Warstwa | Technologia |
|---|---|
| Frontend framework | **React 18 + Vite** |
| Mapa | **React-Leaflet** (jedyna biblioteka map) |
| State management | **Zustand** |
| Data fetching | **TanStack Query (React Query)** |
| Stylowanie | **Tailwind CSS** |
| Wykresy | **Recharts** |
| Routing tras ewakuacji | **OSRM public API** |
| Asystent głosowy | **Web Speech API** + fallback Whisper API (OpenAI) |
| Backend | **Spring Boot 3.x / OpenJDK 21 (LTS)** |
| Eventy | **Spring ApplicationEventPublisher + `@Async`** |
| Live feed | **WebSocket + STOMP** (Spring native + SockJS client) |
| Scraping HTML | **Jsoup** |
| Parsowanie XLSX | **Apache POI** |
| Import GIS | **GeoTools** (GML→GeoJSON, transformacja EPSG) lub własny klient WFS |
| Baza danych | **PostgreSQL 15 + PostGIS** |
| Deploy | **Docker + docker-compose** (dwa tryby) |

### Kluczowe zasady projektowe

1. **Database-first** — jedyne źródło danych runtime to PostgreSQL.
   Pliki seed (`*.sql`) służą wyłącznie do inicjalizacji bazy.

2. **Event-driven** — zmiana stanu zagrożenia publikuje `ThreatUpdatedEvent`.
   Agenci reagują przez `@EventListener @Async`. Kontrolery nigdy nie wywołują
   agentów bezpośrednio.

3. **Config-driven layers** — każda warstwa GIS to jeden rekord w `layer_config`.
   Nowa warstwa = INSERT, zero zmian w kodzie.

4. **Separation of concerns** — każdy agent ma jedną odpowiedzialność:
   `FloodImportAgent` importuje, `IkeAgent` liczy IKE, `DecisionAgent` decyduje.

5. **PostGIS jako silnik geospatialny** — `ST_DWithin`, `ST_Intersects`, `ST_Contains`
   w bazie, nie w Javie ani JavaScript.

6. **Komponenty atomowe** — mapa, panele, kalkulatory, asystent głosowy
   to oddzielne moduły bez twardych zależności.

---

## 2. Tryby uruchomienia

### `docker-compose.yml` — tryb dev

Tylko PostgreSQL + PostGIS. Backend i frontend działają lokalnie.

```yaml
services:
  postgres:
    image: postgis/postgis:15-3.4
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/db:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
volumes:
  postgres_data:
```

### `docker-compose.full.yml` — tryb full-stack

Cały stack w kontenerach. Demo, onboarding, VPS.

```yaml
services:
  postgres:
    image: postgis/postgis:15-3.4
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/db:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build: ./frontend
    ports:
      - "3000:3000"
    environment:
      VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://localhost:8080}
      VITE_WS_URL: ${VITE_WS_URL:-ws://localhost:8080/ws}
    depends_on:
      - backend

volumes:
  postgres_data:
```

---

## 3. Diagram architektury

```
┌──────────────────────────────────────────────────────────────┐
│                        Frontend                               │
│  React 18 + React-Leaflet · Zustand · React Query            │
│  ScenarioPanel · DecisionPanel · Top10Panel                  │
│  Web Speech API · Recharts                                    │
└───────────────────────────┬──────────────────────────────────┘
                            │ REST (JSON) / WebSocket (STOMP)
┌───────────────────────────▼──────────────────────────────────┐
│                    Backend — Spring Boot 3                    │
│                                                               │
│  Controllers                                                  │
│  ├── GeoController      GET  /api/layers/{id} (L-01…L-10)   │
│  ├── AdminBoundaryController POST /api/admin-boundaries/import│
│  ├── EntityController   GET  /api/entities, /api/entities/{id}│
│  ├── ImportController   POST /api/import/*                   │
│  ├── IkeController      GET  /api/ike                        │
│  ├── LayerConfigController GET /api/layers                   │
│  ├── ThreatController   [planned] POST /api/threat/*         │
│  ├── DecisionController [planned] GET  /api/decisions        │
│  └── KalkulatorController [planned] POST /api/calculate/*    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │           Decision Layer (Agents)                    │     │
│  │                                                      │     │
│  │  AdminBoundaryImportAgent (PRG WFS → granice_adm.)  │     │
│  │  IkeAgent ◄── @EventListener(ThreatUpdatedEvent)    │     │
│  │      └──publishes──► IkeRecalculatedEvent            │     │
│  │  WfsGmlParser (parser GML dla WFS)                  │     │
│  │                                                      │     │
│  │  [planned v1.1] FloodImportAgent → ThreatUpdatedEvent│     │
│  │  [planned v1.1] DecisionAgent ◄── IkeRecalculatedEvent│    │
│  │  [planned v1.1] LiveFeedService ◄── oba eventy       │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  Services                                                     │
│  ├── GeoService              ładowanie GeoJSON z plików/bazy │
│  ├── EntityBootstrapService  inicjalizacja entity_registry   │
│  ├── EntityImportService     import/upsert podmiotów         │
│  ├── EntityRegistryService   logika rejestru podmiotów       │
│  ├── AdminBoundaryService    zapytania do granice_adm.       │
│  ├── KalkulatorService       [planned v1.2]                  │
│  └── ScraperService          [planned v1.2] Jsoup + POI      │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                PostgreSQL 15 + PostGIS                        │
│  Jedyne źródło danych runtime.                               │
│  placowka · entity_registry · entity_category · entity_source│
│  strefy_zagrozen · ike_results · layer_config                │
│  granice_administracyjne · miejsca_relokacji · ...           │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Warstwa agentowa — szczegóły

### 4.1 `ThreatUpdatedEvent`

```java
// backend/src/main/java/pl/lublin/dashboard/event/ThreatUpdatedEvent.java
public class ThreatUpdatedEvent extends ApplicationEvent {
    private final String scenariusz;   // "Q10" | "Q100" | "Q500" | "pozar" | "blackout"
    private final String obszar;       // kod powiatu lub bbox "lon_min,lat_min,lon_max,lat_max"
    private final String zrodlo;       // "wfs" | "syntetyczne"
    private final Instant timestamp;
    private final String correlationId; // UUID — do korelacji logów między agentami
}
```

### 4.1a `IkeRecalculatedEvent`

Publikowany przez `IkeAgent` po zakończeniu przeliczania IKE dla wszystkich placówek.
Niesie gotowe wyniki — `DecisionAgent` i `LiveFeedService` mogą z nich skorzystać
bez ponownego odpytywania bazy.

```java
// backend/src/main/java/pl/lublin/dashboard/event/IkeRecalculatedEvent.java
public class IkeRecalculatedEvent extends ApplicationEvent {
    private final String correlationId;     // UUID z ThreatUpdatedEvent który wywołał obliczenie
    private final Instant obliczoneO;       // kiedy zakończono obliczenia
    private final int liczbaPrzetworzonych; // ile placówek przetworzono (łącznie — wliczając pominięte)
    private final int liczbaZWynikiem;      // ile placówek ma ike_score != null
    private final int liczbaNull;           // ile placówek dostało ike_score = null (E6/E11)
    private final List<IkeResultSummary> wyniki; // gotowe wyniki — żeby listenery nie musiały odpytywać bazy
}
```

Typ pomocniczy `IkeResultSummary` — lekki widok wyniku (nie pełne DTO):

```java
public record IkeResultSummary(
    String placowkaKod,
    Double ikeScore,       // null gdy wykluczona (E6/E11)
    String ikeKategoria,   // "czerwony" | "zolty" | "zielony" | "nieznany"
    String celRelokacjiKod // null gdy brak miejsca relokacji
) {}
```

**Zasada:** `DecisionAgent` i `LiveFeedService` słuchają wyłącznie `IkeRecalculatedEvent`,
nie `ThreatUpdatedEvent`. Dzięki temu mają gwarancję, że IKE jest już obliczone zanim
zaczną działać, i mogą użyć pola `wyniki` zamiast odpytywać bazę.

### 4.2 `FloodImportAgent`

```
Odpowiedzialność: import danych WFS → zapis do bazy → publikacja eventu

Wyzwalacz: HTTP POST /api/threat/flood/import

Algorytm:
1. Pobierz GeoJSON z WFS ISOK/RZGW dla (obszar, scenariusz)
   → jeśli WFS niedostępny (timeout/błąd) → użyj fallbacku syntetycznego, zloguj WARN
2. Konwertuj GML → GeoJSON jeśli potrzeba
3. Transformuj układ współrzędnych do EPSG:4326 jeśli inny
4. Usuń istniejące strefy dla tego (obszar, scenariusz) z tabeli strefy_zagrozen
5. Zapisz nowe strefy z zrodlo = 'wfs' lub 'syntetyczne'
6. publisher.publishEvent(new ThreatUpdatedEvent(scenariusz, obszar, zrodlo, now(), UUID))
```

**WFS endpoint ISOK (docelowy):**
```
https://hydro.imgw.pl/model/gis/wfs?
  service=WFS&version=1.0.0&request=GetFeature
  &typeName=STREFAQ100    (lub Q10, Q500)
  &bbox={bbox}
  &srsName=EPSG:2180
```

**Fallback syntetyczny:**
Generuj prostokąty wzdłuż koryt rzek na podstawie bounding boxa powiatu.
Zapisuj z `zrodlo = 'syntetyczne'`. Dane syntetyczne wystarczają do demonstracji
pełnego flow event-driven.

### 4.3 `IkeAgent`

```
Odpowiedzialność: przeliczenie IKE dla wszystkich placówek po zmianie zagrożenia

Wyzwalacz: @EventListener(ThreatUpdatedEvent) + @Async

Algorytm:
1. Pobierz wszystkie placówki z tabeli placowka
2. Dla każdej placówki oblicz IKE (szczegóły: docs/IKE_ALGORITHM.md)
3. Upsert wyników do tabeli ike_results
4. publisher.publishEvent(new IkeRecalculatedEvent(correlationId))

Uwaga: @Async — działa w osobnym wątku, nie blokuje wątku HTTP.
```

### 4.4 `DecisionAgent`

```
Odpowiedzialność: generowanie rekomendacji ewakuacyjnych na podstawie IKE

Wyzwalacz: @EventListener(IkeRecalculatedEvent) + @Async
           ↑ Słucha IkeRecalculatedEvent, nie ThreatUpdatedEvent — gwarancja
             że IKE jest już obliczone. Wyniki dostępne w event.getWyniki().

Algorytm:
1. Pobierz wyniki IKE z event.getWyniki() (bez dodatkowego zapytania do bazy)
2. Dla placówek z IKE >= 0.70: rekomendacja = 'ewakuuj_natychmiast'
3. Dla IKE 0.40-0.69: rekomendacja = 'przygotuj_ewakuacje'
4. Dla IKE < 0.40: rekomendacja = 'monitoruj'
5. Dla IKE = null (ike_kategoria = 'nieznany'): pomijaj — brak rekomendacji
6. Uzasadnienie: wygeneruj na podstawie składowych score_* z tabeli ike_results
   (pobierz tylko dla placówek z rekomendacją 'ewakuuj_natychmiast' i 'przygotuj')
7. Zapisz do tabeli evacuation_decisions z tym samym correlation_id
```

**Wzorzec event-driven między agentami:**
`IkeAgent` → publikuje `IkeRecalculatedEvent` → `DecisionAgent` + `LiveFeedService` słuchają.
Dzięki temu `DecisionAgent` zawsze ma gwarancję świeżych danych IKE i może użyć
`event.getWyniki()` zamiast ponownie odpytywać bazę.

### 4.5 `LiveFeedService`

```
Odpowiedzialność: push aktualizacji przez WebSocket do wszystkich podłączonych klientów

Wyzwalacze:
  @EventListener(ThreatUpdatedEvent)   + @Async  →  push stref zagrożeń
  @EventListener(IkeRecalculatedEvent) + @Async  →  push wyników IKE + rekomendacji

Działanie po ThreatUpdatedEvent:
  - Pobierz świeże strefy z bazy (SELECT * FROM strefy_zagrozen WHERE correlation_id = ?)
  - Push do /topic/layers/L-03 — sygnał LAYER_UPDATED (nie pełny GeoJSON; frontend
    wykonuje invalidateQueries i pobiera dane przez GET /api/layers/L-03)
  - Push do /topic/system — typ: THREAT_IMPORT_COMPLETED (lub THREAT_CLEARED)

Działanie po IkeRecalculatedEvent:
  - Użyj event.getWyniki() — bez dodatkowego zapytania do bazy
  - Push do /topic/ike — lista IkeResultSummary wzbogacona o pola potrzebne frontendowi
  - Pobierz rekomendacje z bazy (SELECT * FROM evacuation_decisions WHERE correlation_id = ?)
  - Push do /topic/decisions — lista EvacuationDecision DTO
  - Push do /topic/system — typ: IKE_RECALCULATED, DECISIONS_GENERATED
```

---

## 5. Struktura katalogów

> Legenda: brak oznaczenia = zaimplementowane, `# [planned]` = zaplanowane (kolejne iteracje)

```
./
│
├── CLAUDE.md
├── docker-compose.yml
├── docker-compose.full.yml
├── .env.example
├── .gitignore
│
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── Dockerfile
│   ├── nginx.conf
│   │
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       │
│       ├── types/
│       │   └── gis.ts                  # typy GeoJSON + FacilityProperties + ThreatZoneProperties
│       │
│       ├── components/
│       │   ├── layout/
│       │   │   ├── AppShell.tsx
│       │   │   ├── Header.tsx
│       │   │   ├── Footer.tsx              # ★ przyciski: zwiń panel, import, reset, kalkulatory
│       │   │   └── NotificationList.tsx    # ★ toast lista — pozycja absolute top-20 right-4
│       │   │
│       │   ├── map/
│       │   │   ├── MapContainer.tsx
│       │   │   ├── EntityPopup.tsx         # ★ popup Leaflet dla każdej jednostki ochrony ludności
│       │   │   └── layers/
│       │   │       ├── EntityLayer.tsx         # ★ markery jednostek ochrony ludności (L-01)
│       │   │       ├── ThreatZoneLayer.tsx     # strefy zagrożenia (L-03)
│       │   │       ├── AdminBoundaryLayer.tsx  # ★ L-08/L-09/L-10 — 3 poziomy admin
│       │   │       ├── HeatmapLayer.tsx        # [planned] L-02
│       │   │       ├── DrogiLayer.tsx          # [planned] L-04
│       │   │       ├── TransportLayer.tsx      # [planned] L-05
│       │   │       ├── RelokacjaLayer.tsx      # [planned] L-06
│       │   │       └── BialePlamiLayer.tsx     # [planned] L-07
│       │   │
│       │   ├── panels/
│       │   │   ├── LayerControlPanel.tsx
│       │   │   ├── EntityFilterPanel.tsx   # ★ filtry jednostek (typ, powiat, IKE)
│       │   │   ├── RegionInfoPanel.tsx
│       │   │   ├── ScenarioPanel.tsx       # [planned] wybór scenariusza zagrożenia
│       │   │   ├── DecisionPanel.tsx       # [planned] rekomendacje DecisionAgent
│       │   │   └── Top10Panel.tsx          # [planned] lista Top 10 IKE
│       │   │
│       │   ├── calculators/               # [planned v1.2]
│       │   │   ├── CalculatorHub.tsx
│       │   │   ├── TransportCalculator.tsx
│       │   │   ├── RelocationCalculator.tsx
│       │   │   └── ThreatSpreadCalculator.tsx
│       │   │
│       │   └── voice/                     # [planned v1.3]
│       │       ├── VoiceAssistant.tsx
│       │       ├── VoiceButton.tsx
│       │       └── CommandParser.ts
│       │
│       ├── store/
│       │   ├── mapStore.ts                 # ★ activeLayers, selectedRegion, isPanelCollapsed
│       │   └── notificationStore.ts        # ★ notifications[], addNotification, removeNotification
│       │
│       ├── hooks/
│       │   ├── useLayerData.ts
│       │   ├── useEntityLayerData.ts       # ★ hook dla EntityLayer (entity_registry)
│       │   ├── useAdminBoundaries.ts       # ★ hook dla L-08/L-09/L-10 z filtrem
│       │   ├── importAdminBoundaries.ts    # wywołanie POST /api/admin-boundaries/import + powiadomienia
│       │   ├── useWebSocket.ts             # [planned]
│       │   ├── useFilters.ts               # [planned]
│       │   └── useVoiceCommands.ts         # [planned v1.3]
│       │
│       ├── services/
│       │   ├── api.ts
│       │   ├── websocketService.ts         # [planned]
│       │   ├── geocoder.ts                 # [planned]
│       │   └── routingService.ts           # [planned]
│       │
│       └── utils/
│           ├── colorScale.ts               # [planned]
│           ├── formatters.ts               # [planned]
│           └── geoUtils.ts                 # [planned]
│
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   │
│   └── src/main/
│       ├── java/pl/lublin/dashboard/
│       │   ├── DashboardApplication.java
│       │   │
│       │   ├── event/                           # [planned v1.1]
│       │   │   ├── ThreatUpdatedEvent.java      # centralny event
│       │   │   └── IkeRecalculatedEvent.java    # event po przeliczeniu IKE
│       │   │
│       │   ├── agent/                           # ★ warstwa agentowa
│       │   │   ├── AdminBoundaryImportAgent.java# ★ import granic z GUGiK PRG WFS
│       │   │   ├── IkeAgent.java                # ★ @EventListener → obliczanie IKE
│       │   │   ├── WfsGmlParser.java            # ★ parser GML dla WFS
│       │   │   ├── FloodImportAgent.java        # [planned v1.2] import WFS → ThreatUpdatedEvent
│       │   │   └── DecisionAgent.java           # [planned v1.1] @EventListener → rekomendacje
│       │   │
│       │   ├── config/
│       │   │   ├── WebSocketConfig.java
│       │   │   ├── CorsConfig.java
│       │   │   ├── DataSourceConfig.java
│       │   │   └── AsyncConfig.java             # [planned v1.1] pula wątków dla agentów
│       │   │
│       │   ├── controller/
│       │   │   ├── GeoController.java           # GET /api/layers/{id} L-01…L-10
│       │   │   ├── AdminBoundaryController.java # ★ POST /api/admin-boundaries/import
│       │   │   ├── EntityController.java        # ★ GET /api/entities, /api/entities/{id}
│       │   │   ├── ImportController.java        # ★ POST /api/import/*
│       │   │   ├── LayerConfigController.java
│       │   │   ├── IkeController.java
│       │   │   ├── ThreatController.java        # [planned v1.1] POST /api/threat/*
│       │   │   ├── DecisionController.java      # [planned v1.1] GET /api/decisions
│       │   │   ├── KalkulatorController.java    # [planned v1.2]
│       │   │   └── ScraperController.java       # [planned v1.2]
│       │   │
│       │   ├── service/
│       │   │   ├── GeoService.java
│       │   │   ├── AdminBoundaryService.java    # ★ zapytania do granice_administracyjne
│       │   │   ├── EntityBootstrapService.java  # ★ inicjalizacja danych entity_registry
│       │   │   ├── EntityImportService.java     # ★ import/upsert rekordów do entity_registry
│       │   │   ├── EntityRegistryService.java   # ★ logika biznesowa rejestru podmiotów
│       │   │   ├── LiveFeedService.java         # [planned v1.1] @EventListener → WebSocket push
│       │   │   ├── KalkulatorService.java       # [planned v1.2]
│       │   │   ├── ScraperService.java          # [planned v1.2]
│       │   │   ├── JsoupScraperService.java     # [planned v1.2]
│       │   │   ├── XlsxParserService.java       # [planned v1.2]
│       │   │   ├── WfsClientService.java        # [planned v1.2] klient WFS (GML→GeoJSON, EPSG)
│       │   │   └── GeocodingService.java        # [planned]
│       │   │
│       │   ├── repository/
│       │   │   ├── PlacowkaRepository.java
│       │   │   ├── GranicaAdministracyjnaRepository.java # ★ granice_administracyjne
│       │   │   ├── EntityRegistryEntryRepository.java    # ★ entity_registry
│       │   │   ├── EntityCategoryRepository.java         # ★ entity_category
│       │   │   ├── EntitySourceRepository.java           # ★ entity_source
│       │   │   ├── EntityAliasRepository.java            # ★ entity_alias
│       │   │   ├── EntityImportBatchRepository.java      # ★ entity_import_batch
│       │   │   ├── StrefaZagrozenRepository.java
│       │   │   ├── IkeResultRepository.java
│       │   │   ├── MiejsceRelokacjiRepository.java
│       │   │   ├── ZasobTransportuRepository.java
│       │   │   ├── LayerConfigRepository.java
│       │   │   └── EvacuationDecisionRepository.java     # [planned v1.1]
│       │   │
│       │   ├── model/
│       │   │   ├── GranicaAdministracyjna.java  # ★ encja granice_administracyjne
│       │   │   ├── Placowka.java                # ★ operacyjna tabela jednostek ochrony ludności
│       │   │   ├── EntityRegistryEntry.java     # ★ ujednolicony rejestr podmiotów (entity_registry)
│       │   │   ├── EntityCategory.java          # ★ kategorie podmiotów (DPS, dom_dziecka, hospicjum…)
│       │   │   ├── EntitySource.java            # ★ źródła danych (mpips, BIP, WFS…)
│       │   │   ├── EntityAlias.java             # ★ alternatywne nazwy/kody podmiotów
│       │   │   ├── EntityImportBatch.java       # ★ log importu partii podmiotów
│       │   │   ├── StrefaZagrozen.java
│       │   │   ├── IkeResult.java
│       │   │   ├── MiejsceRelokacji.java
│       │   │   ├── ZasobTransportu.java
│       │   │   ├── LayerConfig.java
│       │   │   └── EvacuationDecision.java      # [planned v1.1] rekomendacja ewakuacyjna
│       │   │
│       │   ├── handlers/
│       │   │   └── SocketConnectionHandler.java # ★ obsługa połączeń WebSocket
│       │   │
│       │   └── scheduler/
│       │       ├── LayerRefreshScheduler.java   # [planned]
│       │       └── ScraperScheduler.java        # [planned v1.2]
│       │
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           ├── geojson/
│           │   ├── lublin_powiaty.geojson
│           │   ├── lublin_gminy.geojson
│           │   └── README.md               # instrukcja pobrania z GADM 4.1
│           └── db/
│               ├── 01_schema.sql           # DDL wszystkich tabel
│               ├── 02_seed_dps.sql         # placówki (DPS + jednostki ochrony ludności)
│               ├── 03_seed_layers.sql      # konfiguracja warstw GIS
│               ├── 04_seed_relokacja.sql   # miejsca relokacji
│               ├── 05_seed_strefy.sql      # strefy zagrożeń (demo)
│               └── 06_seed_transport.sql   # zasoby transportowe
│
└── docs/
    ├── PRD.md
    ├── ARCHITEKTURA_PLAN.md
    ├── DATA_SCHEMA.md
    ├── IKE_ALGORITHM.md
    ├── API_REFERENCE.md
    └── DEPLOYMENT.md
```

---

## 6. Konfiguracja `@Async`

`AsyncConfig.java` musi definiować pulę wątków dla agentów:

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "agentTaskExecutor")
    public TaskExecutor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);      // IkeAgent, DecisionAgent, LiveFeedService
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("agent-");
        executor.initialize();
        return executor;
    }
}
```

Każdy listener używa tej puli:
```java
@Async("agentTaskExecutor")
@EventListener
public void onThreatUpdated(ThreatUpdatedEvent event) { ... }
```

---

## 7. Schemat danych (fragment)

Pełne DDL: `docs/DATA_SCHEMA.md`.

### Nowa tabela: `evacuation_decisions`

```sql
CREATE TABLE evacuation_decisions (
    id                   SERIAL PRIMARY KEY,
    placowka_kod         VARCHAR(20) REFERENCES placowka(kod),
    ike_score            DECIMAL(5,4),
    rekomendacja         VARCHAR(30) CHECK (rekomendacja IN (
                           'ewakuuj_natychmiast', 'przygotuj_ewakuacje', 'monitoruj'
                         )),
    cel_relokacji_kod    VARCHAR(20) REFERENCES miejsca_relokacji(kod),
    uzasadnienie         TEXT,
    zatwierdzona         BOOLEAN DEFAULT NULL,   -- NULL=oczekuje, TRUE=zatwierdzona, FALSE=odrzucona
    correlation_id       VARCHAR(36),            -- UUID z ThreatUpdatedEvent
    wygenerowano_o       TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_decisions_placowka ON evacuation_decisions(placowka_kod);
CREATE INDEX idx_decisions_correlation ON evacuation_decisions(correlation_id);
```

### Zmiana w tabeli `strefy_zagrozen`

```sql
ALTER TABLE strefy_zagrozen
    ADD COLUMN IF NOT EXISTS scenariusz VARCHAR(10),  -- 'Q10' | 'Q100' | 'Q500' | 'pozar_maly' itp.
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(36);
```

---

## 8. Integracje zewnętrzne

| Serwis | Cel | Klucz API |
|---|---|---|
| OpenStreetMap / Leaflet | Podkład mapowy | Brak |
| Nominatim (OSM) | Geokodowanie | Brak |
| OSRM (public) | Trasy ewakuacji | Brak |
| GADM 4.1 | GeoJSON granic (legacy L-00) | Brak (open data) |
| **GUGiK PRG WFS** | **Granice administracyjne całej Polski (L-08/L-09/L-10)** | **Brak (publiczny WFS)** |
| ISOK / RZGW Hydroportal | WFS dane powodziowe | Brak (publiczny WFS) |
| Web Speech API | Asystent głosowy | Brak |
| Whisper API (OpenAI) | Fallback głosowy | `OPENAI_API_KEY` |
| mpips.gov.pl | Rejestr placówek | Brak (scraping) |

---

## 9. Kolejność implementacji

Szczegółowe zadania agentowe z definicjami ukończenia i krokami weryfikacji:
**`docs/BACKLOG.md`** — jedyne źródło prawdy dla planu implementacji.

Poniżej wyłącznie cele iteracji (co i dlaczego), bez listy kroków:

| Iteracja | Cel |
|---|---|
| **v1.0 — Fundament GIS** | Działająca mapa z jednostkami ochrony ludności. Spring Boot serwuje dane z PostgreSQL przez REST. Entity registry dla ujednoliconego rejestru podmiotów. IKE liczone na żądanie. |
| **v1.1 — Event-driven core** | Pełny flow: wybór scenariusza → ThreatUpdatedEvent → IKE → IkeRecalculatedEvent → rekomendacje + WebSocket push. |
| **v1.2 — Import i kalkulatory** | Prawdziwy import WFS ISOK z fallbackiem syntetycznym. Trzy kalkulatory zasobów. Scraper HTML/XLSX. |
| **v1.3 — UX i głos** | Asystent głosowy (Web Speech API + Whisper). Pełny Docker stack produkcyjny. |

---

## 10. Ryzyka

| Ryzyko | Mitygacja |
|---|---|
| WFS ISOK niedostępny / zmienia schemat | Fallback syntetyczny; cache ostatniego importu w bazie |
| `@Async` — trudniejsze debugowanie | Correlation ID w każdym evencie; szczegółowe logowanie agentów |
| Race condition: DecisionAgent czyta IKE zanim IkeAgent skończy | DecisionAgent słucha `IkeRecalculatedEvent`, nie `ThreatUpdatedEvent` |
| PostGIS wolne przy dużej liczbie placówek | Indeksy GiST; batch processing w IkeAgent |
| GML z WFS w nieoczekiwanym układzie EPSG | Zawsze jawna transformacja w `WfsClientService` przed zapisem |
| PRG WFS niedostępny / zmienia schemat TypeName | Weryfikacja przez `GetCapabilities`; w przypadku błędu — 503 z instrukcją ręcznego importu |
| L-10 (gminy ~2477) zbyt wolne bez uproszczenia geometrii | Wymagany filtr `kod_woj` lub `bbox`; potencjalne ST_Simplify jako DT |
| PRG WFS zwraca GML w paginacji (maxFeatures) | Implementacja pętli z `startIndex` w `AdminBoundaryImportAgent` |
