# CLAUDE.md — Ogólnopolski Dashboard Jednostek Ochrony Ludności

> Ten plik jest automatycznie wczytywany przez Claude Code na początku każdej sesji.
> Zawiera zasady projektu, mapę dokumentacji i aktualny status iteracji.
> Nie modyfikuj tego pliku bez aktualizacji sekcji `## Status projektu`.
> Zawsze używaj mcp Context7 gdy potrzebujesz dokumentację API/biblioteki, tworzysz kod robisz setup/konfigurację bez mojego konkretnego wskazania.

---

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

---

## Paradygmat: event-driven

System działa reaktywnie na zdarzenia. Centralny event to **`ThreatAlertEvent`**.

```
ThreatAlertImportAgent (@Scheduled co N minut lub HTTP POST /api/threats/manual)
    → poziom wody > próg alarmowy
    → INSERT do threat_alert (is_active=true)
    → publisher.publishEvent(new ThreatAlertEvent(...))     [wątek HTTP kończy się tutaj — 202]

        [@Async] NearbyUnitsAgent.onThreatAlert()
            → PostGIS ST_DWithin: entity_registry w radius_km od geom alertu
            → publisher.publishEvent(new NearbyUnitsComputedEvent(...))

        [@Async] LiveFeedService.onThreatAlert()
            → push /topic/threat-alerts

        [@Async] LiveFeedService.onNearbyUnitsComputed()
            → push /topic/nearby-units
```

**Kluczowa zasada:** `NearbyUnitsAgent` i push przez WebSocket reagują na `ThreatAlertEvent` — operator widzi wynik w ciągu < 30 sekund od wykrycia alertu.

---

## Struktura repozytorium

Pełna, aktualna struktura katalogów: `docs/ARCHITEKTURA_PLAN.md` §5.

**Zasada izolacji:** frontend i backend to dwa osobne projekty — nie importują wzajemnie
swojego kodu. Komunikują się wyłącznie przez REST API (JSON) i WebSocket (STOMP).

**Dwa tryby uruchomienia:**
- `docker-compose.yml` — tylko baza danych. Backend i frontend uruchamiane lokalnie
  (pełne debugowanie, hot reload). Tryb codzienny dla developerów.
- `docker-compose.full.yml` — cały stack w kontenerach. Służy do demo, onboardingu
  nowego developera i deploymentu na VPS. Nie wymaga lokalnej instalacji Javy ani Node.js.

---

## Mapa dokumentacji

| Plik | Zawartość |
|---|---|
| `docs/PRD.md` | Wymagania funkcjonalne, model działania, user stories |
| `docs/ARCHITEKTURA_PLAN.md` | Stack, struktura katalogów, agenci, cele iteracji |
| `docs/BACKLOG.md` | **Zadania agentowe** — jedyne źródło prawdy dla planu implementacji. Czytaj przed każdym zadaniem. |
| `docs/DATA_SCHEMA.md` | Schematy SQL i seed files — czytaj przed tworzeniem danych |
| `docs/API_REFERENCE.md` | Kontrakty REST i WebSocket z przykładami request/response |
| `docs/DEPLOYMENT.md` | Uruchomienie dev/prod, zmienne środowiskowe, troubleshooting |

**Zasada:** przed implementacją dowolnego modułu przeczytaj najpierw odpowiednie zadanie
w `docs/BACKLOG.md`, a następnie dokumenty do których ono odsyła.

---

## Stack technologiczny — decyzje ostateczne

### Frontend (`frontend/`)
- **npm workspaces** — monorepo root w `frontend/package.json`; pakiety: `shared/`, `app/`, `widget/`
- **`@psp/shared`** — wspólny kod React/TS (komponenty, hooki, store, typy); zero zależności od Vite/Mendix
- **`frontend/app/`** — standalone Vite app (dev + demo bez Mendix); czyta `VITE_API_BASE_URL`
- **`frontend/widget/`** — Mendix pluggable widget; thin shell → `<GisMapApp apiBaseUrl={springBaseUrl} />`
- **React 19 + Vite** (w `app/`)
- **pluggable-widgets-tools 11** (w `widget/`) — rollup-based build; custom `rollup.config.js` stripuje TS/JSX z `@psp/shared` przed parserem acorn
- **React-Leaflet** — jedyna biblioteka map. Nie używaj MapLibre GL JS.
- **Zustand** — state management
- **TanStack Query (React Query)** — data fetching, cache
- **Tailwind CSS** — stylowanie (v4; `@source "../../shared/src"` w `app/src/index.css`)
- **Recharts** — wykresy w panelach
- **SockJS + @stomp/stompjs** — WebSocket client (live feed)
- **Web Speech API** — asystent głosowy (+ fallback Whisper API)

### Backend (`backend/`)
- **Spring Boot 3.x / OpenJDK 21 (LTS)**
- **PostgreSQL 15 + PostGIS** — jedyne źródło danych runtime
- **Spring Data JPA + Hibernate Spatial**
- **Spring ApplicationEventPublisher + `@Async`** — mechanizm eventów
- **Spring WebSocket + STOMP** — live feed do frontendu
- **Spring Scheduler** — harmonogram automatycznego importu danych
- **GeoTools lub własny klient HTTP** — pobieranie i konwersja danych WFS (GML → GeoJSON, EPSG transformacja)
- **Jsoup** — scraping HTML stron urzędowych
- **Apache POI** — parsowanie XLSX
- **Maven**

### Infrastruktura
- **Docker + docker-compose** (dwa tryby)
- **OSRM public API** — trasy ewakuacji
- **Nominatim OSM** — geokodowanie
- **OpenStreetMap / Leaflet tiles** — podkład mapowy
- **ISOK / RZGW Hydroportal WFS** — dane powodziowe (z syntetycznym fallbackiem)

---

## Zasady projektowe — nakazy i zakazy

### Architektura
- ✅ **Database-first:** jedyne źródło danych runtime to PostgreSQL.
- ✅ **Event-driven:** zmiany stanu zagrożenia publikują `ThreatAlertEvent`.
  Agenci (`NearbyUnitsAgent`, `LiveFeedService`) reagują przez `@EventListener`.
  Nigdy nie wywołuj agentów bezpośrednio z kontrolera.
- ✅ **`@Async` na listenerach:** każdy `@EventListener` reagujący na `ThreatAlertEvent`
  musi być oznaczony `@Async`. Request HTTP kończy się przed uruchomieniem listenerów.
- ✅ Każda warstwa GIS = jeden rekord w tabeli `layer_config`. Dodanie warstwy = INSERT,
  zero zmian w kodzie.
- ✅ Operacje geoprzestrzenne przez **PostGIS** — nie w Javie ani JavaScript.
- ❌ Nie wywołuj `ThreatAlertImportAgent` ani `NearbyUnitsAgent` bezpośrednio z kontrolerów. Jedyna droga to `publisher.publishEvent(new ThreatAlertEvent(...))`.

### Agenci — odpowiedzialności

| Agent / Serwis | Odpowiedzialność | Wyzwalacz |
|---|---|---|
| `AdminBoundaryImportAgent` | Import granic adm. z GUGiK PRG WFS | HTTP `POST /api/admin-boundaries/import` |
| `ThreatAlertImportAgent` | Polling IMGW API + manual trigger → zapis do `threat_alert` → `ThreatAlertEvent` | `@Scheduled` co N minut + HTTP `POST /api/threats/manual` |
| `NearbyUnitsAgent` | PostGIS ST_DWithin: jednostki w zasięgu alertu → `NearbyUnitsComputedEvent` | `@EventListener ThreatAlertEvent` |
| `LiveFeedService` | Push alertów i pobliskich jednostek przez WebSocket | `@EventListener ThreatAlertEvent` + `@EventListener NearbyUnitsComputedEvent` |
| `MendixImportAgent` | Polling Mendix REST API → upsert geom+category do `mendix_unit_cache` | `@Scheduled` co N minut (🔴 ZABLOKOWANE — wymaga docs Mendix API) |

### Dane
- ✅ Pliki seed (`seed_*.sql`) służą wyłącznie do inicjalizacji bazy. Nie są odczytywane
  w runtime.
- ✅ Pola SQL: **snake_case**. Package root Java: `pl.lublin.dashboard`.
- ✅ `zrodlo` w każdym rekordzie: `'syntetyczne'` | `'wfs'` | `'scraping'` | `'mpips'`.

### Kod
- ✅ Nazwy plików: **tylko ASCII** (np. `LayerConfig.tsx`).
- ✅ Każdy endpoint REST zwraca błędy:
  `{ "error": "...", "code": "ERROR_CODE", "timestamp": "..." }`.
- ❌ Nie używaj `@Transactional` na kontrolerach — tylko na serwisach.
- ❌ Nie hardcoduj URL-i backendu w komponentach React — używaj `services/api.ts`.
- ❌ Nie używaj `localStorage` ani `sessionStorage`.

### UI
- ✅ Mapa: minimum 70% szerokości. Panel boczny zwijany.
- ✅ Font minimum 14px. Ciemny motyw (`bg-gray-900` / `bg-gray-800`).
- ✅ Kolory alertów: czerwony `#EF4444` (alert aktywny/krytyczny), żółty `#F59E0B` (ostrzeżenie),
  zielony `#22C55E` (brak alertu).
- ❌ Popup placówki = Leaflet Popup (`EntityPopup.tsx`), nie modal.

---

## Layout aplikacji

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER: [Logo] Dashboard Jednostek Ochrony Ludności  [Status][🎤]│
├──────────────────────────────────────────┬──────────────────────┤
│                                          │  PANEL BOCZNY (30%)  │
│                                          │  ┌────────────────┐  │
│                                          │  │ ScenarioPanel  │  │
│                                          │  │ (wybór scen.)  │  │
│         MAPA (70%)                       │  └────────────────┘  │
│         React-Leaflet                    │  ┌────────────────┐  │
│         + warstwy GIS                    │  │ LayerControl   │  │
│                                          │  │ (toggle+czas)  │  │
│   [Kliknięcie placówki → Popup Leaflet]   │  └────────────────┘  │
│                                          │  ┌────────────────┐  │
│                                          │  │ AlertsPanel    │  │
│                                          │  │ (lista alertów)│  │
│                                          │  └────────────────┘  │
│                                          │  ┌────────────────┐  │
│                                          │  │ NearbyUnits    │  │
│                                          │  │ (jdn. w zasięgu│  │
│                                          │  └────────────────┘  │
├──────────────────────────────────────────┴──────────────────────┤
│  [◀ Zwiń panel]  [🗺 Reset widoku]  [⊕ Kalkulatory]             │
└─────────────────────────────────────────────────────────────────┘
```

**Popup jednostki (`EntityPopup.tsx`):**
```
┌─────────────────────────────────┐
│ 🔵 [Typ] "Nazwa jednostki" [✕]  │
│ Kategoria · Źródło danych       │
│ ─────────────────────────────── │
│ Adres: ul. Przykładowa 1        │
│ Telefon: 81-xxx  Email: ...     │
│ Status: aktywna                 │
│ ─────────────────────────────── │
│ Zasoby (mock):                  │
│  Wóz cysternowy: 2 ✓            │
│  Ponton motorowy: 1 ✓           │
│ ─────────────────────────────── │
│ Alert: 🔴 W ZASIĘGU ZAGROŻENIA  │
│ ─────────────────────────────── │
│ [🔗 Rekord źródłowy]            │
└─────────────────────────────────┘
```

---

## Status projektu

| Iteracja | Status | Deliverable |
|---|---|---|
| v1.0 — Fundament GIS | ✅ Ukończona (1.1–1.12 ✅) | Mapa + granice (PL) + entity registry + Spring Boot + PostGIS |
| REVISION 2 | ✅ | Legacy removal (IKE, DPS tables, test-only layers) + docs update |
| REVISION 1 | ✅ | UX fixes: layer selection per warstwa, viewport |
| DT-LOGS-TESTS | ⬜ | Logi + testy dla istniejących serwisów |
| v1.1 — Zasoby + Alerty | ⬜ | resource_type, entity_resources, threat_alert, ThreatAlertImportAgent (IMGW), NearbyUnitsAgent, WebSocket |
| v1.2 — Importy API | ⬜ | PSP, PRM, RPWDL bulk import + Nominatim geokodowanie + clustering |
| CONCEPT CHANGE — Mendix Widget | ✅ | npm workspaces monorepo + @psp/shared + GisMap widget + mendix_unit_cache schema + JPA entity/repo |

> ⬜ Nie rozpoczęta → 🔄 W toku → ✅ Ukończona

---

## Jak uruchomić projekt lokalnie

```bash
cp .env.example .env   # uzupełnij POSTGRES_PASSWORD

# Tryb dev (zalecany — debugowanie)
docker compose up -d postgres
# backend: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# frontend (standalone): cd frontend/app && npm run dev  → http://localhost:5173
# frontend (widget dev): cd frontend/widget && npm run dev  → http://localhost:3000 (wymaga Studio Pro)

# Tryb full-stack (demo / onboarding)
docker compose -f docker-compose.full.yml up --build
# Frontend: http://localhost:3000  |  Backend: http://localhost:8080
```

**Skróty dla Windows** (uruchamiaj dwuklikiem lub z cmd.exe):
- `start-dev.cmd` — odpowiednik `docker compose up -d postgres`
- `start-all.cmd` — odpowiednik `docker compose -f docker-compose.full.yml up --build`

Szczegóły: `docs/DEPLOYMENT.md`.

---

## Kluczowe pliki — gdzie co szukać

| Szukam... | Ścieżka |
|---|---|
| Definicje eventów | `backend/.../event/ThreatAlertEvent.java` + `NearbyUnitsComputedEvent.java` |
| Alert zagrożenia | `backend/.../agent/ThreatAlertImportAgent.java` + tabela `threat_alert` |
| Pobliskie jednostki | `backend/.../agent/NearbyUnitsAgent.java` |
| Zasoby jednostek | tabela `entity_resources` + `resource_type` |
| Kontrakty API | `docs/API_REFERENCE.md` |
| Schematy SQL + seed | `docs/DATA_SCHEMA.md` |
| Konfiguracja warstw | tabela `layer_config` — seed: `db/03_seed_layers.sql` |
| Rejestr podmiotów | `backend/.../model/EntityRegistryEntry.java` + tabela `entity_registry` |
| Warstwa placówek (frontend) | `frontend/shared/src/components/map/layers/EntityLayer.tsx` |
| Popup placówki (frontend) | `frontend/shared/src/components/map/EntityPopup.tsx` |
| Root komponent (widget + standalone) | `frontend/shared/src/GisMapApp.tsx` |
| Mendix widget thin shell | `frontend/widget/src/GisMap.tsx` |
| Mendix geo-cache entity | `backend/.../model/MendixUnitCache.java` + tabela `mendix_unit_cache` |

---

## Logi i testy — obowiązek na każdym zadaniu

Każdy nowy lub modyfikowany serwis/agent backendu MUSI zawierać:

1. `@Slf4j` lub `private static final Logger log = LoggerFactory.getLogger(X.class);`
2. `log.info("[NazwaKlasy] opis — correlationId={}", id)` na wejściu kluczowych metod
3. `log.error("[NazwaKlasy] błąd — correlationId={}, msg={}", id, e.getMessage())` w catch
4. Testy jednostkowe w `src/test/java/` z `@ExtendWith(MockitoExtension.class)`
   — pokrycie każdej metody z logiką biznesową (nie getterów, nie repozytoriów)

Frontend: vitest dla funkcji w `utils/` i `hooks/` z logiką (nie czysty fetch).

**Brak testów = zadanie nie jest ukończone.**

---

## Reguły pracy agentowej

Ta sekcja obowiązuje w każdej sesji implementacyjnej. Czytaj przed rozpoczęciem zadania.

### Jedno zadanie = jeden commit

Każda sesja implementuje **dokładnie jedno zadanie** z `docs/BACKLOG.md`.
Zadanie jest skończone gdy: kod działa + weryfikacja przeszła.
Nie zaczynaj kolejnego zadania w tej samej sesji.

Docelowy rozmiar zmiany: **50–200 linii kodu netto**. Przekroczenie 300 linii
w jednym zadaniu to sygnał że zadanie było za duże i należało je podzielić.

Nie wykonuj operacji git - zmiany przechodzą przez review człowieka.

```
# Prawidłowy workflow jednej sesji:
1. Przeczytaj zadanie z BACKLOG.md (sekcja "Aktywne zadanie")
2. Przeczytaj wskazane dokumenty referencyjne
3. Zaimplementuj — tylko to co opisuje zadanie, nic więcej
4. Przeprowadź weryfikację z checklisty zadania
5. Przygotuj krótki opis zmian "feat(X.Y): krótki opis"
6. Zaktualizuj status zadania w BACKLOG.md (⬜ → ✅)
7. Koniec sesji
```

### Format komunikatu commit

```
feat(1.2): docker-compose.yml + PostgreSQL + PostGIS
feat(1.5): CorsConfig.java
feat(2.3): NearbyUnitsAgent @EventListener @Async
fix(1.9): poprawka encji entity_category — brakujące pole
```

Prefiks: `feat` dla nowego kodu, `fix` dla poprawek, `docs` dla dokumentacji.
Numer w nawiasie = numer zadania z BACKLOG.md.

### Zakres zadania — czego nie robić

- ❌ Nie implementuj funkcji spoza aktywnego zadania, nawet jeśli "przy okazji" widzisz potrzebę
- ❌ Nie refaktoruj kodu z poprzednich zadań jeśli nie jest to wymagane
- ❌ Nie dodawaj zależności Maven/npm bez wcześniejszego wpisu w zadaniu
- ✅ Jeśli widzisz problem poza zakresem — zapisz go jako notatkę w BACKLOG.md sekcja "Dług techniczny"

### Weryfikacja

Każde zadanie w BACKLOG.md ma własną checklistę weryfikacji.
Ogólna zasada minimalna dla każdego zadania backendowego:

```bash
# 1. Backend kompiluje się bez błędów
./mvnw compile -q

# 2. Baza dostępna i seed wykonany
docker compose ps postgres   # status: healthy
docker compose exec postgres psql -U lublin -d gis_dashboard \
  -c "SELECT COUNT(*) FROM entity_registry;"   # oczekiwane: > 0

# 3. Endpoint odpowiada
curl -s http://localhost:8080/api/layers | jq '. | length'   # oczekiwane: 7
```

Dla zadań frontendowych minimum:
```bash
# Brak błędów TypeScript/ESLint
npm run build   # musi przejść bez błędów
```

### Izolacja kontekstu między sesjami

Każda nowa sesja zaczyna się od przeczytania:
1. Tego pliku (`CLAUDE.md`) — aktualny stan projektu
2. `docs/BACKLOG.md` — które zadanie jest następne
3. Dokumentów wskazanych przez to zadanie

Nie zakładaj że pamiętasz cokolwiek z poprzedniej sesji.
Jeśli coś jest niejasne — sprawdź w dokumentacji, nie zgaduj.

### Ignorowane pliki i katalogi

Nie czytaj ani nie modyfikuj zawartości:
```
node_modules/
dist/
build/
target/
*.geojson          # duże pliki granic — tylko GeoService je czyta
*.class
.env
```

### Kiedy zatrzymać się i zapytać

Zatrzymaj implementację i zadaj pytanie użytkownikowi gdy:
- Zadanie wymaga decyzji architektonicznej nieopisanej w dokumentacji
- Napotkasz sprzeczność między dokumentami
- Weryfikacja nie przechodzi po 2 próbach naprawy
- Zakres zadania okazuje się znacznie większy niż 200 linii
