# CLAUDE.md — Inteligentna Mapa Województwa Lubelskiego

> Ten plik jest automatycznie wczytywany przez Claude Code na początku każdej sesji.
> Zawiera zasady projektu, mapę dokumentacji i aktualny status iteracji.
> Nie modyfikuj tego pliku bez aktualizacji sekcji `## Status projektu`.

---

## Czym jest ten projekt

Geospatial Decision Dashboard dla Marszałka Województwa Lubelskiego.
Moduł główny: **Ewakuacja osób zależnych (DPS-y) w kryzysie** (powódź / pożar / blackout).

Użytkownik docelowy: osoba decyzyjna (nie-programista), pracująca na dużym monitorze
lub tablecie podczas briefingu kryzysowego. System odpowiada na pytanie:
„Które placówki ewakuować, czym i dokąd?" — w czasie < 15 minut od wykrycia kryzysu.

---

## Paradygmat: event-driven

System działa reaktywnie na zdarzenia. Centralny event to **`ThreatUpdatedEvent`**.

```
Użytkownik wybiera scenariusz zagrożenia (np. powódź Q100)
    → FloodImportAgent pobiera dane z WFS (ISOK/RZGW) lub generuje syntetyczne
    → zapisuje do tabeli strefy_zagrozen
    → publisher.publishEvent(new ThreatUpdatedEvent(...))     [wątek HTTP kończy się tutaj — 202]

        [@Async] IkeAgent.onThreatUpdated()
            → przelicza IKE dla wszystkich placówek
            → zapisuje wyniki do ike_results
            → publisher.publishEvent(new IkeRecalculatedEvent(...))

        [@Async] LiveFeedService.onThreatUpdated()
            → pushuje /topic/layers/L-03 (nowe strefy) i /topic/system (status importu)

        [@Async] DecisionAgent.onIkeRecalculated()          ← słucha IkeRecalculatedEvent
            → generuje rekomendacje ewakuacyjne na podstawie gotowych wyników IKE
            → zapisuje do evacuation_decisions

        [@Async] LiveFeedService.onIkeRecalculated()        ← słucha IkeRecalculatedEvent
            → pushuje /topic/ike i /topic/decisions
            → frontend odbiera event i odświeża mapę automatycznie
```

**Kluczowa zasada sekwencji:** `DecisionAgent` i push IKE/rekomendacji przez WebSocket
reagują na `IkeRecalculatedEvent` — **nie** na `ThreatUpdatedEvent`. Gwarantuje to
że rekomendacje są zawsze generowane na podstawie kompletnych wyników IKE,
eliminując race condition.

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
| `docs/IKE_ALGORITHM.md` | Formuła IKE, wagi, edge case'y, powiązanie z eventami |
| `docs/API_REFERENCE.md` | Kontrakty REST i WebSocket z przykładami request/response |
| `docs/DEPLOYMENT.md` | Uruchomienie dev/prod, zmienne środowiskowe, troubleshooting |

**Zasada:** przed implementacją dowolnego modułu przeczytaj najpierw odpowiednie zadanie
w `docs/BACKLOG.md`, a następnie dokumenty do których ono odsyła.

---

## Stack technologiczny — decyzje ostateczne

### Frontend (`frontend/`)
- **React 18 + Vite**
- **React-Leaflet** — jedyna biblioteka map. Nie używaj MapLibre GL JS.
- **Zustand** — state management
- **TanStack Query (React Query)** — data fetching, cache
- **Tailwind CSS** — stylowanie
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
- ✅ **Event-driven:** zmiany stanu zagrożenia publikują `ThreatUpdatedEvent`.
  Agenci (`IkeAgent`, `DecisionAgent`, `LiveFeedService`) reagują przez `@EventListener`.
  Nigdy nie wywołuj agentów bezpośrednio z kontrolera.
- ✅ **`@Async` na listenerach:** każdy `@EventListener` reagujący na `ThreatUpdatedEvent`
  musi być oznaczony `@Async`. Request HTTP kończy się przed uruchomieniem listenerów.
- ✅ Każda warstwa GIS = jeden rekord w tabeli `layer_config`. Dodanie warstwy = INSERT,
  zero zmian w kodzie.
- ✅ Logika IKE wyłącznie w `IkeAgent`. Frontend konsumuje wynik przez REST lub WebSocket.
- ✅ Operacje geoprzestrzenne przez **PostGIS** — nie w Javie ani JavaScript.
- ❌ Nie wywołuj `IkeAgent` ani `DecisionAgent` bezpośrednio z kontrolerów.
  Jedyna droga to `publisher.publishEvent(new ThreatUpdatedEvent(...))`.

### Agenci — odpowiedzialności

| Agent / Serwis | Odpowiedzialność | Wyzwalacz |
|---|---|---|
| `FloodImportAgent` | Pobieranie danych WFS, konwersja GML→GeoJSON, zapis do `strefy_zagrozen`, publikacja `ThreatUpdatedEvent` | HTTP `POST /api/threat/flood/import` |
| `IkeAgent` | Przeliczanie IKE dla wszystkich placówek, zapis do `ike_results`, publikacja `IkeRecalculatedEvent` | `@EventListener ThreatUpdatedEvent` |
| `DecisionAgent` | Generowanie rekomendacji ewakuacyjnych na podstawie gotowych wyników IKE, zapis do `evacuation_decisions` | `@EventListener IkeRecalculatedEvent` |
| `LiveFeedService` | Push stref i statusu po imporcie; push IKE i rekomendacji po przeliczeniu | `@EventListener ThreatUpdatedEvent` (strefy) + `@EventListener IkeRecalculatedEvent` (IKE/decyzje) |

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
- ✅ Kolory IKE: czerwony `#EF4444` (≥0.70), żółty `#F59E0B` (0.40–0.69),
  zielony `#22C55E` (<0.40).
- ❌ Popup DPS = Leaflet Popup, nie modal.

---

## Layout aplikacji

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER: [Logo] Inteligentna Mapa Woj. Lubelskiego  [Status][🎤]│
├──────────────────────────────────────────┬──────────────────────┤
│                                          │  PANEL BOCZNY (30%)  │
│                                          │  ┌────────────────┐  │
│                                          │  │ ScenarioPanel  │  │
│                                          │  │ (wybór scen.)  │  │
│         MAPA (70%)                       │  └────────────────┘  │
│         React-Leaflet                    │  ┌────────────────┐  │
│         + warstwy GIS                    │  │ LayerControl   │  │
│                                          │  │ (toggle+czas)  │  │
│   [Kliknięcie DPS → Popup Leaflet]       │  └────────────────┘  │
│                                          │  ┌────────────────┐  │
│                                          │  │ Top10Panel     │  │
│                                          │  │ (lista IKE)    │  │
│                                          │  └────────────────┘  │
│                                          │  ┌────────────────┐  │
│                                          │  │ DecisionPanel  │  │
│                                          │  │ (rekomendacje) │  │
│                                          │  └────────────────┘  │
├──────────────────────────────────────────┴──────────────────────┤
│  [◀ Zwiń panel]  [🗺 Reset widoku]  [⊕ Kalkulatory]             │
└─────────────────────────────────────────────────────────────────┘
```

**Popup DPS:**
```
┌─────────────────────────────────┐
│ 🔴 DPS "Nazwa placówki"   [✕]  │
│ Powiat: lubelski · Gmina: ...   │
│ ─────────────────────────────── │
│ Podopieczni: 45 (32 niesamodz.) │
│ Pojemność: 60 · Obsada: 12 os.  │
│ Generator: ✅  Kontakt: 81-xxx  │
│ ─────────────────────────────── │
│ IKE: 0.82 🔴 EWAKUACJA NATYCH. │
│ ─────────────────────────────── │
│ [📍 Pokaż trasę ewakuacji]      │
│ [🏠 Najbliższe miejsce relokacji]│
└─────────────────────────────────┘
```

---

## Status projektu

| Iteracja | Status | Deliverable |
|---|---|---|
| v1.0 — Fundament GIS | 🔄 W toku (1.1–1.10 ✅, 1.11 ⬜) | Mapa + granice (PL) + DPS-y + Spring Boot + PostGIS |
| v1.1 — Event-driven core | ⬜ Nie rozpoczęta | ThreatUpdatedEvent + IkeAgent + DecisionAgent + WebSocket |
| v1.2 — Import i kalkulatory | ⬜ Nie rozpoczęta | FloodImportAgent (WFS) + 3 kalkulatory + Scraper |
| v1.3 — UX i głos | ⬜ Nie rozpoczęta | ScenarioPanel + asystent głosowy + Docker prod |

> ⬜ Nie rozpoczęta → 🔄 W toku → ✅ Ukończona

---

## Jak uruchomić projekt lokalnie

```bash
cp .env.example .env   # uzupełnij POSTGRES_PASSWORD

# Tryb dev (zalecany — debugowanie)
docker compose up -d postgres
# backend: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# frontend: npm run dev

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
| Definicja eventu | `backend/.../event/ThreatUpdatedEvent.java` |
| Logika IKE | `backend/.../agent/IkeAgent.java` + `docs/IKE_ALGORITHM.md` |
| Import WFS | `backend/.../agent/FloodImportAgent.java` |
| Rekomendacje | `backend/.../agent/DecisionAgent.java` |
| Kontrakty API | `docs/API_REFERENCE.md` |
| Schematy SQL + seed | `docs/DATA_SCHEMA.md` |
| Konfiguracja warstw | tabela `layer_config` — seed: `db/seed_layers.sql` |
| Wagi IKE | `backend/src/main/resources/ike.config.json` (frontend pobiera przez `GET /api/ike/config`) |

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
feat(2.3): IkeAgent refaktor na @EventListener @Async
fix(1.9): poprawka encji Placowka — brakujące pole geom
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
  -c "SELECT COUNT(*) FROM placowka;"   # oczekiwane: 48

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
