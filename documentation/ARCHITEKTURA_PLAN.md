# ARCHITEKTURA_PLAN.md — Inteligentna Mapa Województwa Lubelskiego

> Dokument planistyczny — stack technologiczny, struktura katalogów, plan iteracji.
> Wersja: 1.2 · Data: 2026-04-14

---

## 1. Decyzje architektoniczne

### Stack technologiczny

| Warstwa | Technologia |
|---|---|
| Frontend framework | **React 18 + Vite** |
| Mapa | **React-Leaflet** (jedyna biblioteka map — nie używaj MapLibre GL JS) |
| State management | **Zustand** |
| Data fetching | **TanStack Query (React Query)** |
| Stylowanie | **Tailwind CSS** |
| Wykresy / statystyki | **Recharts** |
| Routing tras ewakuacji | **OSRM public API** (`https://router.project-osrm.org`) |
| Asystent głosowy | **Web Speech API** + fallback Whisper API (OpenAI) |
| Backend | **Spring Boot 3.x / OpenJDK 21 (LTS)** |
| Live feed | **WebSocket + STOMP** (Spring native + SockJS client) |
| Scraping HTML | **Jsoup** |
| Parsowanie XLSX | **Apache POI** |
| Baza danych | **PostgreSQL 15 + PostGIS** |
| Deploy | **Docker + docker-compose** (dwa tryby — patrz sekcja 2) |

### Kluczowe zasady projektowe

1. **Database-first** — jedyne źródło danych runtime to PostgreSQL.
   Pliki seed (`*.sql`, `*.json` w `src/data/`) służą wyłącznie do inicjalizacji bazy
   i nie są odczytywane przez aplikację w trakcie działania.

2. **Config-driven layers** — każda warstwa GIS to jeden rekord w tabeli `layer_config`.
   Dodanie nowej warstwy = wstawienie rekordu do bazy, zero zmian w kodzie.

3. **Separation of concerns** — logika IKE wyłącznie w `IkeService.java`;
   frontend konsumuje gotowe wyniki przez REST.

4. **PostGIS jako silnik geospatialny** — zapytania promieniowe (`ST_DWithin`),
   nakładanie poligonów (`ST_Intersects`, `ST_Contains`) i indeksy GiST
   realizowane w bazie, nie w aplikacji.

5. **Komponenty atomowe** — mapa, panel boczny, kalkulatory, social feed
   i asystent głosowy to oddzielne moduły bez twardych zależności między sobą.

---

## 2. Tryby uruchomienia (Docker)

Projekt dostarcza dwa pliki docker-compose odpowiadające dwóm scenariuszom użycia.

### `docker-compose.yml` — tryb dev (codzienna praca)

Uruchamia **tylko PostgreSQL + PostGIS**. Backend i frontend działają lokalnie
(pełne debugowanie w IntelliJ, Vite HMR bez opóźnień).

```yaml
# docker-compose.yml
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

Uruchomienie:
```bash
docker compose up -d postgres
```

### `docker-compose.full.yml` — tryb full-stack (demo / onboarding / VPS)

Uruchamia **cały stack**: baza + backend + frontend w kontenerach.
Nie wymaga lokalnej instalacji Javy ani Node.js.
Używany do szybkiego demo, onboardingu nowego developera i deploymentu na VPS.

```yaml
# docker-compose.full.yml
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

Uruchomienie:
```bash
docker compose -f docker-compose.full.yml up --build
```

---

## 3. Diagram architektury

```
┌──────────────────────────────────────────────────────┐
│                     Frontend                          │
│  React 18  +  React-Leaflet                          │
│  Tailwind CSS · Zustand · React Query                │
│  Web Speech API · Recharts                            │
└───────────────────────┬──────────────────────────────┘
                        │ REST (JSON) / WebSocket (STOMP)
┌───────────────────────▼──────────────────────────────┐
│          Backend — Spring Boot 3 / OpenJDK 21         │
│                                                       │
│  spring-web (REST Controllers)                        │
│  spring-websocket + STOMP (live layer feeds)          │
│  spring-scheduler (automatyczne odświeżanie)          │
│                                                       │
│  ├── GeoController      — serwowanie GeoJSON warstw   │
│  ├── IkeService          — logika IKE                 │
│  ├── KalkulatorService  — kalkulatory zasobów         │
│  ├── ScraperService     — Jsoup + Apache POI          │
│  └── SocialMediaService — agent social media          │
└───────────────────────┬──────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────┐
│          PostgreSQL 15 + PostGIS                      │
│                                                       │
│  Jedyne źródło danych runtime.                       │
│  Geometrie · Warstwy · IKE results · Cache            │
│                                                       │
│  Dane ładowane przez skrypty seed przy init.          │
└──────────────────────────────────────────────────────┘
```

---

## 4. Struktura katalogów

```
gis-dashboard/
│
├── CLAUDE.md
├── docker-compose.yml              # tryb dev: tylko postgres
├── docker-compose.full.yml         # tryb full-stack: postgres + backend + frontend
├── .env.example
├── .gitignore
│
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── tailwind.config.js
│   ├── Dockerfile                  # używany przez docker-compose.full.yml
│   ├── nginx.conf                  # konfiguracja Nginx dla kontenera frontend
│   │
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       │
│       ├── config/
│       │   ├── layers.config.json  # konfiguracja warstw (wczytywana przez backend)
│       │   └── ike.config.json     # wagi IKE (wczytywane przez IkeService)
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
│       │   │       ├── TransportPopup.jsx
│       │   │       └── SocialMediaPin.jsx
│       │   │
│       │   ├── panels/
│       │   │   ├── LayerControlPanel.jsx
│       │   │   ├── FilterPanel.jsx
│       │   │   ├── Top10Panel.jsx
│       │   │   ├── RegionInfoPanel.jsx
│       │   │   └── SocialMediaPanel.jsx
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
│       │   ├── useVoiceCommands.js
│       │   └── useSocialMediaFeed.js
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
│   ├── Dockerfile                  # używany przez docker-compose.full.yml
│   │
│   └── src/main/
│       ├── java/pl/lublin/dashboard/
│       │   ├── DashboardApplication.java
│       │   │
│       │   ├── config/
│       │   │   ├── WebSocketConfig.java
│       │   │   ├── SchedulerConfig.java
│       │   │   ├── CorsConfig.java
│       │   │   └── DataSourceConfig.java
│       │   │
│       │   ├── controller/
│       │   │   ├── GeoController.java
│       │   │   ├── LayerConfigController.java
│       │   │   ├── IkeController.java
│       │   │   ├── KalkulatorController.java
│       │   │   ├── ScraperController.java
│       │   │   └── SocialMediaController.java
│       │   │
│       │   ├── service/
│       │   │   ├── GeoService.java
│       │   │   ├── IkeService.java
│       │   │   ├── KalkulatorService.java
│       │   │   ├── ScraperService.java
│       │   │   ├── JsoupScraperService.java
│       │   │   ├── XlsxParserService.java
│       │   │   ├── SocialMediaService.java
│       │   │   ├── GeocodingService.java
│       │   │   └── LiveFeedService.java
│       │   │
│       │   ├── repository/
│       │   │   ├── PlacowkaRepository.java
│       │   │   ├── LayerConfigRepository.java
│       │   │   ├── RelokacjaRepository.java
│       │   │   └── TransportRepository.java
│       │   │
│       │   ├── model/
│       │   │   ├── Placowka.java
│       │   │   ├── MiejsceRelokacji.java
│       │   │   ├── ZasobTransportu.java
│       │   │   ├── LayerConfig.java
│       │   │   └── IkeResult.java
│       │   │
│       │   └── scheduler/
│       │       ├── LayerRefreshScheduler.java
│       │       └── ScraperScheduler.java
│       │
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/
│               ├── schema.sql          # DDL: tabele + rozszerzenie PostGIS
│               ├── seed_layers.sql     # konfiguracja 7 warstw GIS
│               ├── seed_dps.sql        # 48 placówek DPS (po 2 na powiat)
│               ├── seed_relokacja.sql  # miejsca relokacji
│               ├── seed_transport.sql  # zasoby transportowe
│               ├── seed_strefy.sql     # strefy zagrożeń (syntetyczne)
│               └── seed_social.sql     # feed social media (demonstracyjny)
│
└── docs/
    ├── PRD.md
    ├── ARCHITEKTURA_PLAN.md    ← ten plik
    ├── DATA_SCHEMA.md
    ├── IKE_ALGORITHM.md
    ├── API_REFERENCE.md
    └── DEPLOYMENT.md
```

---

## 5. Schemat danych kluczowych

Pełne DDL z wszystkimi tabelami i walidacjami: `docs/DATA_SCHEMA.md` sekcja 8.

### Tabela `placowka` (fragment)

```sql
CREATE TABLE placowka (
    id                       SERIAL PRIMARY KEY,
    kod                      VARCHAR(20) UNIQUE NOT NULL,
    nazwa                    VARCHAR(255) NOT NULL,
    typ                      VARCHAR(30),
    powiat                   VARCHAR(100) NOT NULL,
    gmina                    VARCHAR(100) NOT NULL,
    geom                     GEOMETRY(Point, 4326) NOT NULL,
    pojemnosc_ogolna         INTEGER,
    liczba_podopiecznych     INTEGER,
    niesamodzielni_procent   DECIMAL(4,3),
    generator_backup         BOOLEAN DEFAULT FALSE,
    personel_dyzurny         INTEGER,
    kontakt                  VARCHAR(50),
    ostatnia_aktualizacja    TIMESTAMPTZ DEFAULT NOW(),
    zrodlo                   VARCHAR(20) DEFAULT 'syntetyczne'
);
CREATE INDEX idx_placowka_geom ON placowka USING GIST(geom);
```

### `ike.config.json` — schemat wag

Plik wczytywany przez `IkeService.java` przy starcie (`@PostConstruct`).

```json
{
  "wagi": {
    "zagrozenie": 0.35,
    "niesamodzielni": 0.25,
    "transport_brak": 0.20,
    "droznosc_brak": 0.15,
    "odleglosc_relokacji": 0.05
  },
  "progi": { "czerwony": 0.70, "zolty": 0.40 },
  "promienie_km": { "transport_dostepny": 15, "miejsca_relokacji": 50 }
}
```

---

## 6. Algorytm IKE

Formuła, wagi, edge case'y i przykłady obliczeń: `docs/IKE_ALGORITHM.md`.

```
IKE = 0.35 × score_zagrozenia
    + 0.25 × score_niesamodzielnych
    + 0.20 × score_braku_transportu
    + 0.15 × score_braku_droznosci
    + 0.05 × score_odleglosci_relokacji
```

IKE ∈ [0, 1]:
- **≥ 0.70** → czerwony (ewakuacja natychmiastowa)
- **0.40–0.69** → żółty (przygotowanie)
- **< 0.40** → zielony (brak bezpośredniego zagrożenia)

---

## 7. Integracje zewnętrzne

| Serwis | Cel | Klucz API |
|---|---|---|
| OpenStreetMap / Leaflet | Podkład mapowy | Brak |
| Nominatim (OSM) | Geokodowanie adresów i toponimów | Brak |
| OSRM (public) | Wyznaczanie tras ewakuacji | Brak |
| GUGiK / GADM 4.1 | GeoJSON granic administracyjnych | Brak (open data) |
| Web Speech API | Asystent głosowy (przeglądarka) | Brak |
| Whisper API (OpenAI) | Fallback asystenta głosowego | `OPENAI_API_KEY` |
| mpips.gov.pl | Rejestr placówek pomocy społecznej | Brak (scraping HTML) |

---

## 8. Kolejność implementacji

### ITERACJA v1.0 — Fundament GIS

> Cel: działająca mapa z DPS-ami i granicami województwa, Spring Boot serwujący dane
> z PostgreSQL przez REST.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 1.1 | `docker-compose.yml` | PostgreSQL + PostGIS w kontenerze, profil dev |
| 1.2 | `db/schema.sql` | DDL: tabele + rozszerzenie PostGIS |
| 1.3 | `DashboardApplication.java` + `pom.xml` | Setup Spring Boot 3, zależności Maven |
| 1.4 | `DataSourceConfig.java` + `application-dev.yml` | Konfiguracja połączenia z PostgreSQL |
| 1.5 | `CorsConfig.java` | CORS dla frontendu (localhost:5173 + domena docelowa) |
| 1.6 | `db/seed_layers.sql` | Seed konfiguracji 7 warstw GIS |
| 1.7 | `db/seed_dps.sql` | Seed 48 placówek DPS |
| 1.8 | Pliki GeoJSON granic | Pobranie z GADM 4.1, zapis do `backend/src/main/resources/geojson/` |
| 1.9 | `db/seed_strefy.sql` | Seed syntetycznych stref zagrożenia |
| 1.10 | `Placowka.java` + `PlacowkaRepository.java` | Encja JPA + repository |
| 1.11 | `LayerConfig.java` + `LayerConfigRepository.java` | Encja JPA + repository |
| 1.12 | `GeoService.java` | Ładowanie GeoJSON z plików i danych z bazy |
| 1.13 | `GeoController.java` + `LayerConfigController.java` | REST: `GET /api/layers` i `GET /api/layers/{id}` |
| 1.14 | `frontend/package.json` + `vite.config.js` | Setup Vite + React 18 + Tailwind |
| 1.15 | `services/api.js` | Klient axios — base URL z `VITE_API_BASE_URL` |
| 1.16 | `components/layout/AppShell.jsx` | Layout 70/30 (mapa / panel boczny) |
| 1.17 | `components/layout/Header.jsx` | Nagłówek z tytułem i statusem systemu |
| 1.18 | `components/map/MapContainer.jsx` | Leaflet z podkładem OSM, viewport na Lublin |
| 1.19 | `components/map/AdminBoundaries.jsx` | GeoJSON powiatów i gmin z hover/click |
| 1.20 | `components/map/layers/DPSLayer.jsx` | Markery DPS-ów z kolorowaniem wg IKE |
| 1.21 | `components/map/popups/DPSPopup.jsx` | Popup z danymi placówki |
| 1.22 | `components/panels/RegionInfoPanel.jsx` | Panel z info o klikniętym powiecie/gminie |
| 1.23 | `components/map/layers/ZagrozeniaLayer.jsx` | Warstwa stref zagrożenia (poligony) |

**Deliverable v1.0:** Działająca mapa z DPS-ami, granicami i strefami zagrożeń.
Spring Boot serwuje dane przez REST z PostgreSQL.

---

### ITERACJA v1.1 — Logika kryzysowa

> Cel: kompletne 7 warstw, algorytm IKE, panel Top 10, trasy ewakuacji, WebSocket.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 2.1 | `db/seed_relokacja.sql` + `db/seed_transport.sql` | Seed miejsc relokacji i transportu |
| 2.2 | `MiejsceRelokacji.java` + `ZasobTransportu.java` | Encje JPA + repozytoria |
| 2.3 | `IkeService.java` | Algorytm IKE — zapytania PostGIS + ranking |
| 2.4 | `IkeResult.java` | DTO wyniku IKE |
| 2.5 | `IkeController.java` | REST: `GET /api/ike`, `GET /api/ike/{kod}`, `POST /api/ike/recalculate` |
| 2.6 | `components/map/layers/HeatmapLayer.jsx` | Warstwa L-02 |
| 2.7 | `components/map/layers/DrogiLayer.jsx` | Warstwa L-04 |
| 2.8 | `components/map/layers/TransportLayer.jsx` | Warstwa L-05 |
| 2.9 | `components/map/layers/RelokacjaLayer.jsx` | Warstwa L-06 |
| 2.10 | `components/map/layers/BialePlamiLayer.jsx` | Warstwa L-07 |
| 2.11 | `components/map/LayerManager.jsx` | Logika włączania/wyłączania warstw (Zustand) |
| 2.12 | `components/panels/LayerControlPanel.jsx` | UI przełączników warstw z timestampami |
| 2.13 | `components/panels/FilterPanel.jsx` | Filtry regionu, typu, zagrożenia, IKE |
| 2.14 | `hooks/useFilters.js` | Stan filtrów w Zustand |
| 2.15 | `utils/colorScale.js` | Mapowanie IKE → kolor markera |
| 2.16 | `components/ui/IKEScore.jsx` | Wizualizacja IKE (kolor, liczba, label) |
| 2.17 | `components/panels/Top10Panel.jsx` | Panel „Top 10 do ewakuacji" |
| 2.18 | `services/routingService.js` | Integracja z OSRM |
| 2.19 | `components/map/EvacuationRoute.jsx` | Rysowanie trasy na mapie |
| 2.20 | `WebSocketConfig.java` | Konfiguracja STOMP + SockJS |
| 2.21 | `LiveFeedService.java` | Publikowanie aktualizacji przez WebSocket |
| 2.22 | `LayerRefreshScheduler.java` | Cykliczne odświeżanie warstw |
| 2.23 | `services/websocketService.js` | SockJS + STOMP client (React) |
| 2.24 | `hooks/useWebSocket.js` | Hook subskrypcji live feed |
| 2.25 | `components/ui/StatusIndicator.jsx` | Spinner / ikona statusu przy warstwie |
| 2.26 | `components/map/MapControls.jsx` | Zoom, reset widoku, fullscreen |

**Deliverable v1.1:** Kompletne 7 warstw, IKE z rankingiem Top 10,
trasy ewakuacji, live feed przez WebSocket.

---

### ITERACJA v1.2 — Moduły dodatkowe

> Cel: scraper danych z urzędów, 3 kalkulatory zasobów, zapytania PostGIS.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 3.1 | `JsoupScraperService.java` | Scraper HTML (mpips.gov.pl, BIP powiatów) |
| 3.2 | `XlsxParserService.java` | Parser XLSX (Apache POI) |
| 3.3 | `GeocodingService.java` | Geokodowanie adresów (Nominatim) |
| 3.4 | `ScraperService.java` | Orchestrator: Jsoup + POI + Geocoding → PostgreSQL |
| 3.5 | `ScraperController.java` | REST: `POST /api/scraper/run`, `GET /api/scraper/log` |
| 3.6 | `ScraperScheduler.java` | Harmonogram automatyczny (co 24h) |
| 3.7 | `KalkulatorService.java` | Logika kalkulatorów (`ST_DWithin` w PostGIS) |
| 3.8 | `KalkulatorController.java` | REST: `POST /api/calculate/transport`, `relocation`, `threat` |
| 3.9 | `components/calculators/TransportCalculator.jsx` | Kalkulator transportu ewakuacyjnego |
| 3.10 | `components/calculators/RelocationCalculator.jsx` | Kalkulator miejsc relokacji |
| 3.11 | `components/calculators/ThreatSpreadCalculator.jsx` | Kalkulator zasięgu zagrożenia |
| 3.12 | `components/calculators/CalculatorHub.jsx` | Drawer z wyborem kalkulatora |
| 3.13 | `hooks/useLayerData.js` | Hook React Query — pobieranie + cache + auto-refresh |

**Deliverable v1.2:** Scraper pobiera dane z ≥1 publicznego źródła.
Wszystkie 3 kalkulatory działają z wynikami na mapie.

---

### ITERACJA v1.3 — AI & głos, deploy

> Cel: agent social media, asystent głosowy, full-stack Docker, dokumentacja wdrożeniowa.

| Krok | Plik / zadanie | Opis |
|---|---|---|
| 4.1 | `db/seed_social.sql` | 25+ demonstracyjnych postów z geolokalizacją |
| 4.2 | `SocialMediaService.java` | Agent: parsowanie feed, ekstrakcja toponimów, geocoding |
| 4.3 | `SocialMediaController.java` | REST: `GET /api/social/feed` |
| 4.4 | `hooks/useSocialMediaFeed.js` | Hook pobierania feed |
| 4.5 | `components/map/popups/SocialMediaPin.jsx` | Popup pinezki na mapie |
| 4.6 | `components/panels/SocialMediaPanel.jsx` | Panel „Ostatnie sygnały" |
| 4.7 | `components/voice/CommandParser.js` | Logika parsowania komend PL (regex + intent) |
| 4.8 | `hooks/useVoiceCommands.js` | Hook Web Speech API + dispatcher akcji |
| 4.9 | `components/voice/VoiceButton.jsx` | Przycisk mikrofonu z animacją |
| 4.10 | `components/voice/VoiceAssistant.jsx` | Kontener: transkrypcja, feedback, fallback |
| 4.11 | `services/geocoder.js` | Geokodowanie toponimów z komend głosowych |
| 4.12 | `backend/Dockerfile` | Obraz Docker Spring Boot (OpenJDK 21) |
| 4.13 | `frontend/Dockerfile` + `nginx.conf` | Obraz Docker React (Nginx) |
| 4.14 | `docker-compose.full.yml` | Kompletny stack: postgres + backend + frontend |
| 4.15 | `application-prod.yml` | Konfiguracja produkcyjna (pool, GC, logi) |

**Deliverable v1.3:** Kompletny system z asystentem głosowym, agentem social media,
pełnym stackiem Docker i gotową dokumentacją wdrożeniową.

---

## 9. Ryzyka i mitygacje

| Ryzyko | Mitygacja |
|---|---|
| GeoJSON granic niedostępny w dobrej jakości | GADM 4.1 jako źródło — instrukcja pobierania w `docs/DATA_SCHEMA.md` |
| Web Speech API złe rozpoznawanie PL | Fallback Whisper API; przyciski predefiniowanych komend w UI |
| OSRM public API niedostępne | Cache gotowych tras dla Top 10 placówek w tabeli `ike_results` |
| Scraper MPIPS zmienia HTML | Selektory CSS w konfiguracji + last-successful cache w bazie |
| Wydajność mapy przy 7 warstwach | MarkerCluster, lazy loading, zapytania PostGIS z `ST_MakeEnvelope` |
