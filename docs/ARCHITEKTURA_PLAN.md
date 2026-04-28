# ARCHITEKTURA_PLAN.md — Ogólnopolski Dashboard Jednostek Ochrony Ludności

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

2. **Event-driven** — wykrycie alertu publikuje `ThreatAlertEvent`.
   Agenci reagują przez `@EventListener @Async`. Kontrolery nigdy nie wywołują
   agentów bezpośrednio.

3. **Config-driven layers** — każda warstwa GIS to jeden rekord w `layer_config`.
   Nowa warstwa = INSERT, zero zmian w kodzie.

4. **Separation of concerns** — każdy agent ma jedną odpowiedzialność:
   `ThreatAlertImportAgent` importuje alerty IMGW, `NearbyUnitsAgent` oblicza zasięg.

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
│  AlertPanel · NearbyUnitsPanel · LayerControlPanel           │
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
│  ├── LayerConfigController GET /api/layers                   │
│  ├── ThreatController   [planned v1.1] POST /api/threats/manual│
│  └── KalkulatorController [planned v1.2] POST /api/calculate/*│
│                                                               │
│  ┌─────────────────────────────────────────────────────┐     │
│  │           Decision Layer (Agents)                    │     │
│  │                                                      │     │
│  │  AdminBoundaryImportAgent (PRG WFS → granice_adm.)  │     │
│  │  MendixImportAgent (@Scheduled → mendix_unit_cache) 🔴│   │
│  │  ThreatAlertImportAgent (@Scheduled IMGW + manual)  │     │
│  │          └──publishes──► ThreatAlertEvent            │     │
│  │  NearbyUnitsAgent ◄── @EventListener(ThreatAlertEvent)│    │
│  │          └──publishes──► NearbyUnitsComputedEvent    │     │
│  │  LiveFeedService ◄── ThreatAlertEvent + NearbyUnitsComputedEvent│
│  │  WfsGmlParser (parser GML dla WFS)                  │     │
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
│  entity_registry · entity_category · entity_source            │
│  threat_alert · layer_config                                  │
│  granice_administracyjne · ...                                │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Warstwa agentowa — szczegóły

### 4.1 `ThreatAlertEvent`

Publikowany przez `ThreatAlertImportAgent` gdy poziom wody > próg lub operator tworzy manualny alert.

```java
public class ThreatAlertEvent extends ApplicationEvent {
    private final Long alertId;
    private final String threatType;    // "flood" | "fire" | "blackout"
    private final String level;         // "warning" | "alarm" | "emergency"
    private final String sourceApi;     // "imgw_hydro" | "manual"
    private final Double lat;
    private final Double lon;
    private final Double radiusKm;
    private final String correlationId;
    // konstruktor + gettery
}
```

### 4.2 `NearbyUnitsComputedEvent`

Publikowany przez `NearbyUnitsAgent` po obliczeniu listy jednostek w zasięgu.

```java
public class NearbyUnitsComputedEvent extends ApplicationEvent {
    private final String correlationId;
    private final List<Long> entityIds;  // ID jednostek z entity_registry
    private final String alertId;
    // konstruktor + gettery
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
2. publisher.publishEvent(new NearbyUnitsComputedEvent(correlationId, entityIds, alertId))
```

### 4.5 `MendixImportAgent`

```
Klasa: MendixImportAgent
Odpowiedzialność: Polling Mendix REST API → upsert geom+category do mendix_unit_cache
Wyzwalacz: @Scheduled co N minut (🔴 ZABLOKOWANE — wymaga docs Mendix API)
```

### 4.6 `LiveFeedService`

```
Wyzwalacze:
  @EventListener(ThreatAlertEvent)         → push /topic/threat-alerts
  @EventListener(NearbyUnitsComputedEvent) → push /topic/nearby-units
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
├── frontend/                         ← CAŁY frontend (npm workspaces root)
│   ├── package.json                    ← workspace root: workspaces: [shared, app, widget]
│   ├── node_modules/                   ← hoisted workspace deps
│   │
│   ├── shared/                         ← @psp/shared: czyste React+TS, zero Vite/Mendix
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   └── src/
│   │       ├── index.ts                    ← barrel export
│   │       ├── GisMapApp.tsx               ← root komponent (props: apiBaseUrl, initialZoom)
│   │       ├── services/
│   │       │   ├── api.ts                  ← createApiClient(baseUrl)
│   │       │   └── ApiContext.tsx          ← React Context dla axios instance
│   │       ├── components/
│   │       │   ├── layout/                 ← Header, AppShell, NotificationList
│   │       │   ├── map/                    ← MapContainer, EntityPopup
│   │       │   ├── map/layers/             ← EntityLayer, AdminBoundaryLayer
│   │       │   └── panels/                 ← LayerControlPanel, EntityFilterPanel, RegionInfoPanel
│   │       ├── hooks/                      ← useLayerData, useAdminBoundaries, useEntityLayerData...
│   │       ├── store/                      ← mapStore, notificationStore
│   │       └── types/
│   │           └── gis.ts
│   │
│   ├── app/                            ← standalone Vite (dev + demo bez Mendix)
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── index.html
│   │   └── src/
│   │       ├── main.tsx                ← thin shell: VITE_API_BASE_URL → GisMapApp
│   │       └── index.css               ← Tailwind + @source dla shared/
│   │
│   └── widget/                         ← Mendix pluggable widget
│       ├── package.json
│       ├── rollup.config.js            ← custom: babel plugin stripuje TS/JSX z @psp/shared
│       └── src/
│           ├── GisMap.tsx              ← thin shell: props Mendix → GisMapApp
│           ├── GisMap.xml             ← deklaracja właściwości widżetu
│           └── ui/
│               └── GisMap.css
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
│       │   │   ├── ThreatAlertEvent.java        # centralny event alertu
│       │   │   └── NearbyUnitsComputedEvent.java # event po obliczeniu zasięgu
│       │   │
│       │   ├── agent/                           # ★ warstwa agentowa
│       │   │   ├── AdminBoundaryImportAgent.java# ★ import granic z GUGiK PRG WFS
│       │   │   ├── ThreatAlertImportAgent.java  # [planned v1.1] @Scheduled IMGW + manual
│       │   │   ├── NearbyUnitsAgent.java        # [planned v1.1] @EventListener → ST_DWithin
│       │   │   └── WfsGmlParser.java            # ★ parser GML dla WFS
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
│       │   │   ├── ThreatController.java        # [planned v1.1] POST /api/threats/manual
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
│       │   │   ├── GranicaAdministracyjnaRepository.java # ★ granice_administracyjne
│       │   │   ├── EntityRegistryEntryRepository.java    # ★ entity_registry
│       │   │   ├── EntityCategoryRepository.java         # ★ entity_category
│       │   │   ├── EntitySourceRepository.java           # ★ entity_source
│       │   │   ├── EntityAliasRepository.java            # ★ entity_alias
│       │   │   ├── EntityImportBatchRepository.java      # ★ entity_import_batch
│       │   │   ├── ThreatAlertRepository.java            # [planned v1.1]
│       │   │   └── LayerConfigRepository.java
│       │   │
│       │   ├── model/
│       │   │   ├── GranicaAdministracyjna.java  # ★ encja granice_administracyjne
│       │   │   ├── EntityRegistryEntry.java     # ★ ujednolicony rejestr podmiotów (entity_registry)
│       │   │   ├── EntityCategory.java          # ★ kategorie podmiotów (DPS, dom_dziecka, hospicjum…)
│       │   │   ├── EntitySource.java            # ★ źródła danych (mpips, BIP, WFS…)
│       │   │   ├── EntityAlias.java             # ★ alternatywne nazwy/kody podmiotów
│       │   │   ├── EntityImportBatch.java       # ★ log importu partii podmiotów
│       │   │   ├── ThreatAlert.java             # [planned v1.1] alert zagrożenia
│       │   │   └── LayerConfig.java
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
        executor.setCorePoolSize(3);      // ThreatAlertImportAgent, NearbyUnitsAgent, LiveFeedService
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
public void onThreatAlert(ThreatAlertEvent event) { ... }
```

---

## 7. Schemat danych (fragment)

Pełne DDL: `docs/DATA_SCHEMA.md`.

### Nowa tabela: `threat_alert`

```sql
CREATE TABLE threat_alert (
    id            BIGSERIAL PRIMARY KEY,
    external_id   VARCHAR(100),                 -- ID stacji IMGW lub UUID manualny
    source_api    VARCHAR(30) NOT NULL,         -- 'imgw_hydro' | 'manual'
    threat_type   VARCHAR(20) NOT NULL,         -- 'flood' | 'fire' | 'blackout'
    level         VARCHAR(20) NOT NULL,         -- 'warning' | 'alarm' | 'emergency'
    geom          GEOMETRY(Point, 4326),
    radius_km     DECIMAL(8,2),
    is_active     BOOLEAN DEFAULT TRUE,
    correlation_id VARCHAR(36),
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at    TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_threat_alert_active ON threat_alert(is_active);
CREATE INDEX idx_threat_alert_geom ON threat_alert USING GIST(geom);
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
| **v1.0 — Fundament GIS** | Mapa, entity registry, granice PRG. ✅ |
| **REVISION 2** | Usunięcie legacy (IKE, DPS, test-only layers). |
| **REVISION 1** | UX fixes: layer selection per warstwa, viewport. |
| **DT-LOGS-TESTS** | Logi + testy dla istniejących serwisów. |
| **v1.1 — Zasoby + Alerty** | resource_type, entity_resources, threat_alert, ThreatAlertImportAgent (IMGW), NearbyUnitsAgent, WebSocket push. |
| **v1.2 — Importy API** | PSP/PRM/RPWDL bulk import + geokodowanie Nominatim + clustering. |
| **v1.3 — UX i głos** | Asystent głosowy + Docker prod. |

---

## 10. Ryzyka

| Ryzyko | Mitygacja |
|---|---|
| IMGW API niedostępne / zmienia schemat | Logi WARN + retry z exponential backoff; cache ostatnich odczytów w bazie |
| `@Async` — trudniejsze debugowanie | Correlation ID w każdym evencie; szczegółowe logowanie agentów |
| PostGIS wolne przy dużej liczbie jednostek | Indeksy GiST na geom; ST_DWithin z indeksem przestrzennym |
| GML z WFS w nieoczekiwanym układzie EPSG | Zawsze jawna transformacja w `WfsClientService` przed zapisem |
| PRG WFS niedostępny / zmienia schemat TypeName | Weryfikacja przez `GetCapabilities`; w przypadku błędu — 503 z instrukcją ręcznego importu |
| L-10 (gminy ~2477) zbyt wolne bez uproszczenia geometrii | Wymagany filtr `kod_woj` lub `bbox`; potencjalne ST_Simplify jako DT |
| PRG WFS zwraca GML w paginacji (maxFeatures) | Implementacja pętli z `startIndex` w `AdminBoundaryImportAgent` |
