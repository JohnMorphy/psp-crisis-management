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
│  ├── ThreatController   POST /api/threat/flood/import        │
│  │                      POST /api/threat/clear               │
│  ├── GeoController      GET  /api/layers/{id}                │
│  ├── IkeController      GET  /api/ike                        │
│  ├── DecisionController GET  /api/decisions                  │
│  └── KalkulatorController POST /api/calculate/*              │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │           Decision Layer (Agents)                    │     │
│  │                                                      │     │
│  │  FloodImportAgent ──publishes──► ThreatUpdatedEvent  │     │
│  │                                         │            │     │
│  │  IkeAgent ◄── @EventListener ───────────┤            │     │
│  │  DecisionAgent ◄── @EventListener ──────┤            │     │
│  │  LiveFeedService ◄── @EventListener ────┘            │     │
│  └─────────────────────────────────────────────────────┘     │
│                                                               │
│  Services                                                     │
│  ├── GeoService         ładowanie GeoJSON z plików i bazy    │
│  ├── KalkulatorService  kalkulatory zasobów (PostGIS)        │
│  └── ScraperService     Jsoup + Apache POI                   │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                PostgreSQL 15 + PostGIS                        │
│  Jedyne źródło danych runtime.                               │
│  placowka · strefy_zagrozen · ike_results                    │
│  evacuation_decisions · layer_config · ...                   │
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
  - Push do /topic/layers/L-03 — pełny GeoJSON zaktualizowanych stref
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

```
gis-dashboard/
│
├── CLAUDE.md
├── docker-compose.yml
├── docker-compose.full.yml
├── .env.example
├── .gitignore
│
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   ├── Dockerfile
│   ├── nginx.conf
│   │
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       │
│       ├── config/
│       │   ├── layers.config.json      # konfiguracja warstw (fallback offline)
│       │   └── ike.config.json         # wagi IKE
│       │
│       ├── components/
│       │   ├── layout/
│       │   │   ├── AppShell.jsx
│       │   │   ├── Header.jsx
│       │   │   └── Sidebar.jsx
│       │   │
│       │   ├── map/
│       │   │   ├── MapContainer.jsx
│       │   │   ├── MapControls.jsx
│       │   │   ├── LayerManager.jsx
│       │   │   ├── AdminBoundaries.jsx
│       │   │   ├── EvacuationRoute.jsx
│       │   │   ├── layers/
│       │   │   │   ├── DPSLayer.jsx
│       │   │   │   ├── HeatmapLayer.jsx
│       │   │   │   ├── ZagrozeniaLayer.jsx
│       │   │   │   ├── DrogiLayer.jsx
│       │   │   │   ├── TransportLayer.jsx
│       │   │   │   ├── RelokacjaLayer.jsx
│       │   │   │   └── BialePlamiLayer.jsx
│       │   │   └── popups/
│       │   │       ├── DPSPopup.jsx
│       │   │       └── TransportPopup.jsx
│       │   │
│       │   ├── panels/
│       │   │   ├── ScenarioPanel.jsx       # ★ wybór scenariusza zagrożenia
│       │   │   ├── DecisionPanel.jsx       # ★ rekomendacje DecisionAgent
│       │   │   ├── LayerControlPanel.jsx
│       │   │   ├── FilterPanel.jsx
│       │   │   ├── Top10Panel.jsx
│       │   │   └── RegionInfoPanel.jsx
│       │   │
│       │   ├── calculators/
│       │   │   ├── CalculatorHub.jsx
│       │   │   ├── TransportCalculator.jsx
│       │   │   ├── RelocationCalculator.jsx
│       │   │   └── ThreatSpreadCalculator.jsx
│       │   │
│       │   ├── voice/
│       │   │   ├── VoiceAssistant.jsx
│       │   │   ├── VoiceButton.jsx
│       │   │   └── CommandParser.js
│       │   │
│       │   └── ui/
│       │       ├── Badge.jsx
│       │       ├── StatusIndicator.jsx
│       │       ├── IKEScore.jsx
│       │       ├── Tooltip.jsx
│       │       └── Modal.jsx
│       │
│       ├── hooks/
│       │   ├── useLayerData.js
│       │   ├── useWebSocket.js
│       │   ├── useFilters.js
│       │   └── useVoiceCommands.js
│       │
│       ├── services/
│       │   ├── api.js
│       │   ├── websocketService.js
│       │   ├── geocoder.js
│       │   └── routingService.js
│       │
│       └── utils/
│           ├── colorScale.js
│           ├── formatters.js
│           └── geoUtils.js
│
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   │
│   └── src/main/
│       ├── java/pl/lublin/dashboard/
│       │   ├── DashboardApplication.java
│       │   │
│       │   ├── event/
│       │   │   ├── ThreatUpdatedEvent.java      # ★ centralny event
│       │   │   └── IkeRecalculatedEvent.java    # ★ event po przeliczeniu IKE
│       │   │
│       │   ├── agent/                           # ★ warstwa agentowa
│       │   │   ├── FloodImportAgent.java        # import WFS → publikacja ThreatUpdatedEvent
│       │   │   ├── IkeAgent.java               # @EventListener → obliczanie IKE
│       │   │   └── DecisionAgent.java          # @EventListener → rekomendacje
│       │   │
│       │   ├── config/
│       │   │   ├── AsyncConfig.java            # ★ konfiguracja @Async (pula wątków)
│       │   │   ├── WebSocketConfig.java
│       │   │   ├── CorsConfig.java
│       │   │   └── DataSourceConfig.java
│       │   │
│       │   ├── controller/
│       │   │   ├── ThreatController.java       # ★ POST /api/threat/*
│       │   │   ├── GeoController.java
│       │   │   ├── LayerConfigController.java
│       │   │   ├── IkeController.java
│       │   │   ├── DecisionController.java     # ★ GET /api/decisions
│       │   │   ├── KalkulatorController.java
│       │   │   └── ScraperController.java
│       │   │
│       │   ├── service/
│       │   │   ├── GeoService.java
│       │   │   ├── LiveFeedService.java        # @EventListener → WebSocket push
│       │   │   ├── KalkulatorService.java
│       │   │   ├── ScraperService.java
│       │   │   ├── JsoupScraperService.java
│       │   │   ├── XlsxParserService.java
│       │   │   ├── WfsClientService.java       # ★ klient WFS (GML→GeoJSON, EPSG)
│       │   │   └── GeocodingService.java
│       │   │
│       │   ├── repository/
│       │   │   ├── PlacowkaRepository.java
│       │   │   ├── StrefaZagrozenRepository.java
│       │   │   ├── IkeResultRepository.java
│       │   │   ├── EvacuationDecisionRepository.java
│       │   │   ├── LayerConfigRepository.java
│       │   │   ├── RelokacjaRepository.java
│       │   │   └── TransportRepository.java
│       │   │
│       │   ├── model/
│       │   │   ├── Placowka.java
│       │   │   ├── StrefaZagrozen.java
│       │   │   ├── IkeResult.java
│       │   │   ├── EvacuationDecision.java     # ★ rekomendacja ewakuacyjna
│       │   │   ├── MiejsceRelokacji.java
│       │   │   ├── ZasobTransportu.java
│       │   │   └── LayerConfig.java
│       │   │
│       │   └── scheduler/
│       │       ├── LayerRefreshScheduler.java
│       │       └── ScraperScheduler.java
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
│               ├── schema.sql
│               ├── seed_layers.sql
│               ├── seed_dps.sql
│               ├── seed_relokacja.sql
│               ├── seed_transport.sql
│               └── seed_strefy.sql
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
| GADM 4.1 / GUGiK | GeoJSON granic | Brak (open data) |
| ISOK / RZGW Hydroportal | WFS dane powodziowe | Brak (publiczny WFS) |
| Web Speech API | Asystent głosowy | Brak |
| Whisper API (OpenAI) | Fallback głosowy | `OPENAI_API_KEY` |
| mpips.gov.pl | Rejestr placówek | Brak (scraping) |

---

## 9. Kolejność implementacji

### ITERACJA v1.0 — Fundament GIS

> Cel: działająca mapa z DPS-ami, Spring Boot serwuje dane z PostgreSQL przez REST.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 1.1 | `docker-compose.yml` | PostgreSQL + PostGIS w kontenerze |
| 1.2 | `db/schema.sql` | DDL: wszystkie tabele + PostGIS |
| 1.3 | `DashboardApplication.java` + `pom.xml` | Setup Spring Boot 3, zależności Maven |
| 1.4 | `DataSourceConfig.java` + `application-dev.yml` | Połączenie z PostgreSQL |
| 1.5 | `CorsConfig.java` | CORS dla frontendu |
| 1.6 | `db/seed_layers.sql` + `db/seed_dps.sql` | Seed 7 warstw + 48 DPS-ów |
| 1.7 | Pliki GeoJSON granic | Pobieranie z GADM 4.1 → `resources/geojson/` |
| 1.8 | `db/seed_strefy.sql` | Seed syntetycznych stref zagrożenia |
| 1.9 | Encje JPA + repozytoria | `Placowka`, `LayerConfig`, `StrefaZagrozen` |
| 1.10 | `GeoService.java` | Ładowanie GeoJSON z plików i bazy |
| 1.11 | `GeoController.java` + `LayerConfigController.java` | `GET /api/layers`, `GET /api/layers/{id}` |
| 1.12 | `IkeAgent.java` (wersja uproszczona) | Obliczanie IKE bez eventów (na żądanie) |
| 1.13 | `IkeController.java` | `GET /api/ike`, `GET /api/ike/{kod}` |
| 1.14 | Vite + React 18 + Tailwind | Setup frontend |
| 1.15 | `services/api.js` | Klient axios |
| 1.16 | `AppShell.jsx` + `Header.jsx` | Layout 70/30 |
| 1.17 | `MapContainer.jsx` | Leaflet, viewport na Lublin |
| 1.18 | `AdminBoundaries.jsx` | GeoJSON powiatów i gmin |
| 1.19 | `DPSLayer.jsx` + `DPSPopup.jsx` | Markery DPS-ów z IKE |
| 1.20 | `ZagrozeniaLayer.jsx` | Strefy zagrożeń (poligony) |
| 1.21 | `RegionInfoPanel.jsx` + `LayerControlPanel.jsx` | Panele boczne |

**Deliverable v1.0:** Mapa z DPS-ami i strefami. IKE liczone na żądanie przez REST.

---

### ITERACJA v1.1 — Event-driven core

> Cel: pełny flow event-driven — wybór scenariusza → event → IKE → rekomendacje → WebSocket.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 2.1 | `ThreatUpdatedEvent.java` + `IkeRecalculatedEvent.java` | Klasy eventów |
| 2.2 | `AsyncConfig.java` | Pula wątków dla agentów (`@EnableAsync`) |
| 2.3 | `IkeAgent.java` (refaktor) | Zmiana z serwisu na `@EventListener @Async` |
| 2.4 | `DecisionAgent.java` | `@EventListener(IkeRecalculatedEvent)` → rekomendacje |
| 2.5 | `EvacuationDecision.java` + `EvacuationDecisionRepository.java` | Encja + repozytorium |
| 2.6 | `FloodImportAgent.java` (stub) | Przyjmuje request, generuje syntetyczne strefy, publikuje event |
| 2.7 | `ThreatController.java` | `POST /api/threat/flood/import`, `POST /api/threat/clear` |
| 2.8 | `WebSocketConfig.java` | STOMP + SockJS |
| 2.9 | `LiveFeedService.java` | `@EventListener` → push przez WebSocket |
| 2.10 | `DecisionController.java` | `GET /api/decisions` |
| 2.11 | `services/websocketService.js` + `hooks/useWebSocket.js` | WebSocket client React |
| 2.12 | `components/panels/ScenarioPanel.jsx` | UI wyboru scenariusza |
| 2.13 | `components/panels/DecisionPanel.jsx` | UI rekomendacji |
| 2.14 | `Top10Panel.jsx` + `FilterPanel.jsx` | Panele z IKE |
| 2.15 | Pozostałe warstwy L-02, L-04…L-07 | Komponenty React + dane seed |
| 2.16 | `utils/colorScale.js` + `IKEScore.jsx` | Kolorowanie wg IKE |
| 2.17 | `EvacuationRoute.jsx` + `routingService.js` | Trasy OSRM |

**Deliverable v1.1:** Pełny flow event-driven. Wybór scenariusza uruchamia
automatyczne przeliczenie IKE i rekomendacje widoczne w UI przez WebSocket.

---

### ITERACJA v1.2 — Import WFS i kalkulatory

> Cel: prawdziwy import z WFS ISOK z fallbackiem syntetycznym, 3 kalkulatory, scraper.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 3.1 | `WfsClientService.java` | Klient HTTP dla WFS ISOK (GML→GeoJSON, EPSG:2180→4326) |
| 3.2 | `FloodImportAgent.java` (pełny) | Prawdziwy WFS + fallback syntetyczny |
| 3.3 | `KalkulatorService.java` | Logika kalkulatorów z `ST_DWithin` |
| 3.4 | `KalkulatorController.java` | `POST /api/calculate/transport`, `relocation`, `threat` |
| 3.5 | `TransportCalculator.jsx` | UI kalkulator 1 |
| 3.6 | `RelocationCalculator.jsx` | UI kalkulator 2 |
| 3.7 | `ThreatSpreadCalculator.jsx` | UI kalkulator 3 |
| 3.8 | `CalculatorHub.jsx` | Drawer z wyborem kalkulatora |
| 3.9 | `ScraperService.java` + `JsoupScraperService.java` | Scraper HTML |
| 3.10 | `XlsxParserService.java` | Parser XLSX |
| 3.11 | `ScraperController.java` | `POST /api/scraper/run`, `GET /api/scraper/log` |
| 3.12 | `hooks/useLayerData.js` | React Query + auto-refresh |

**Deliverable v1.2:** Import z WFS działa (z fallbackiem). Wszystkie 3 kalkulatory
zwracają wyniki. Scraper pobiera dane z ≥1 źródła.

---

### ITERACJA v1.3 — UX i głos

> Cel: asystent głosowy, pełny Docker stack, testy wydajnościowe.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 4.1 | `CommandParser.js` + `hooks/useVoiceCommands.js` | Komendy głosowe PL |
| 4.2 | `VoiceAssistant.jsx` + `VoiceButton.jsx` | UI asystenta |
| 4.3 | `services/geocoder.js` | Geokodowanie toponimów z komend |
| 4.4 | `backend/Dockerfile` | Obraz Docker Spring Boot |
| 4.5 | `frontend/Dockerfile` + `nginx.conf` | Obraz Docker Nginx |
| 4.6 | `docker-compose.full.yml` (finalizacja) | Kompletny stack |
| 4.7 | `application-prod.yml` | Konfiguracja produkcyjna |

**Deliverable v1.3:** Kompletny system z asystentem głosowym i gotowym stackiem Docker.

---

## 10. Ryzyki

| Ryzyko | Mitygacja |
|---|---|
| WFS ISOK niedostępny / zmienia schemat | Fallback syntetyczny; cache ostatniego importu w bazie |
| `@Async` — trudniejsze debugowanie | Correlation ID w każdym evencie; szczegółowe logowanie agentów |
| Race condition: DecisionAgent czyta IKE zanim IkeAgent skończy | DecisionAgent słucha `IkeRecalculatedEvent`, nie `ThreatUpdatedEvent` |
| PostGIS wolne przy dużej liczbie placówek | Indeksy GiST; batch processing w IkeAgent |
| GML z WFS w nieoczekiwanym układzie EPSG | Zawsze jawna transformacja w `WfsClientService` przed zapisem |
