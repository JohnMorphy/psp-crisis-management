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

## Struktura repozytorium

```
gis-dashboard/                  ← katalog główny repo (jeden Git)
├── CLAUDE.md                   ← ten plik
├── docker-compose.yml          ← tryb dev: tylko PostgreSQL + PostGIS
├── docker-compose.full.yml     ← tryb full-stack: postgres + backend + frontend
├── .env.example
├── .gitignore
├── frontend/                   ← aplikacja React (Vite)
│   ├── package.json
│   └── src/
├── backend/                    ← aplikacja Spring Boot (Maven)
│   ├── pom.xml
│   └── src/
└── docs/                       ← dokumentacja projektu
    ├── PRD.md
    ├── ARCHITEKTURA_PLAN.md
    ├── DATA_SCHEMA.md
    ├── IKE_ALGORITHM.md
    ├── API_REFERENCE.md
    └── DEPLOYMENT.md
```

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
| `docs/PRD.md` | Wymagania funkcjonalne i niefunkcjonalne, user stories, kryteria akceptacji |
| `docs/ARCHITEKTURA_PLAN.md` | Stack technologiczny, struktura katalogów, plan iteracji krok po kroku |
| `docs/DATA_SCHEMA.md` | Schematy SQL i JSON seed dla każdego datasetu — czytaj przed tworzeniem/modyfikacją danych |
| `docs/IKE_ALGORITHM.md` | Formuła algorytmu IKE z wagami, edge case'ami i przykładami obliczeń |
| `docs/API_REFERENCE.md` | Kontrakty REST: endpoint, metoda, request body, response (z przykładami JSON) |
| `docs/DEPLOYMENT.md` | Instrukcja uruchomienia dev i prod, zmienne środowiskowe, troubleshooting |

**Zasada:** przed implementacją dowolnego modułu przeczytaj najpierw odpowiednią sekcję
`docs/ARCHITEKTURA_PLAN.md` (plan iteracji), a następnie `docs/DATA_SCHEMA.md` jeśli
tworzysz lub konsumujesz dane.

---

## Stack technologiczny — decyzje ostateczne

### Frontend (`frontend/`)
- **React 18 + Vite** — framework UI
- **React-Leaflet** — jedyna biblioteka map. Nie używaj MapLibre GL JS.
- **Zustand** — state management (aktywne warstwy, filtry, stan UI)
- **TanStack Query (React Query)** — data fetching, cache, auto-refresh
- **Tailwind CSS** — stylowanie
- **Recharts** — wykresy i statystyki w panelach bocznych
- **SockJS + @stomp/stompjs** — WebSocket client (live feed)
- **Web Speech API** — asystent głosowy (wbudowany w przeglądarkę, bez klucza API)

### Backend (`backend/`)
- **Spring Boot 3.x / OpenJDK 21 (LTS)** — framework backendowy
- **PostgreSQL 15 + PostGIS** — jedyne źródło danych runtime
- **Spring Data JPA + Hibernate Spatial** — ORM z obsługą geometrii PostGIS
- **Spring WebSocket + STOMP** — live feed do frontendu
- **Spring Scheduler** — automatyczne odświeżanie warstw
- **Jsoup** — scraping HTML stron urzędowych
- **Apache POI** — parsowanie plików XLSX z urzędów
- **Maven** — zarządzanie zależnościami (nie Gradle)

### Infrastruktura
- **Docker + docker-compose** — środowisko dev (baza) i full-stack deploy
- **OSRM public API** — wyznaczanie tras ewakuacji (`https://router.project-osrm.org`)
- **Nominatim OSM** — geokodowanie adresów (`https://nominatim.openstreetmap.org`)
- **OpenStreetMap / Leaflet tiles** — podkład mapowy (bez klucza API)

---

## Zasady projektowe — nakazy i zakazy

### Architektura i dane
- ✅ **Database-first:** jedyne źródło danych runtime to **PostgreSQL**.
  Pliki w `frontend/src/data/` i `backend/src/main/resources/db/` to wyłącznie
  materiał do seedowania bazy — nie są odczytywane przez aplikację w runtime.
- ✅ Logika IKE żyje **wyłącznie** w `IkeService.java`. Frontend konsumuje wynik przez REST.
- ✅ Każda warstwa GIS opisana jest jednym rekordem w tabeli `layer_config`.
  Dodanie nowej warstwy = zmiana tylko w bazie, zero zmian w kodzie aplikacji.
- ✅ Operacje geoprzestrzenne realizowane przez **PostGIS**
  (`ST_DWithin`, `ST_Contains`, `ST_Intersects`) — nie w Javie ani JavaScript.
- ✅ Komponenty React są atomowe — mapa, panel boczny, kalkulatory, social feed
  i asystent głosowy to oddzielne moduły bez twardych zależności między sobą.

### Dane
- ✅ Pliki seed (`seed_dps.sql`, `seed_relokacja.sql` itp.) używają **prawdziwych nazw
  miejscowości i powiatów** województwa lubelskiego.
- ✅ Schematy SQL są źródłem prawdy o strukturze danych — patrz `docs/DATA_SCHEMA.md`.
  Pliki JSON w `frontend/src/data/` są generowane ze schematów SQL, nie odwrotnie.
- ✅ Pola w SQL używają **snake_case** (np. `liczba_podopiecznych`, `niesamodzielni_procent`).
- ✅ Źródło każdego rekordu oznaczone jest polem `zrodlo`:
  `'syntetyczne'` | `'scraping'` | `'mpips'`.

### Kod
- ✅ Nazwy plików: **tylko ASCII**, bez polskich znaków.
  Poprawnie: `BialePlamiLayer.jsx`, nie `BiałePlamiLayer.jsx`.
- ✅ Komponenty React: **PascalCase**. Hooki: `use` + PascalCase. Serwisy JS: camelCase.
- ✅ Klasy Java: **PascalCase**. Metody i pola: **camelCase**.
  Package root: `pl.lublin.dashboard`.
- ✅ Każdy endpoint REST zwraca błędy w formacie:
  `{ "error": "Opis błędu", "code": "ERROR_CODE", "timestamp": "..." }`.
- ❌ Nie używaj `@Transactional` na kontrolerach Spring — tylko na serwisach.
- ❌ Nie hardcoduj URL-i backendu w komponentach React —
  używaj stałych z `frontend/src/services/api.js`.
- ❌ Nie używaj `localStorage` ani `sessionStorage` —
  stan żyje w Zustand lub React Query cache.

### UI
- ✅ Mapa zajmuje **minimum 70% szerokości ekranu** (domyślny podział 70/30).
- ✅ Panel boczny jest **zwijany** — po zwinięciu mapa zajmuje 100%.
- ✅ Minimum font: **14px** dla wszystkich etykiet na mapie i w panelach.
- ✅ Kolory IKE:
  - Czerwony `#EF4444` — IKE ≥ 0.70 (ewakuacja natychmiastowa)
  - Żółty `#F59E0B` — IKE 0.40–0.69 (przygotowanie)
  - Zielony `#22C55E` — IKE < 0.40 (brak bezpośredniego zagrożenia)
- ✅ Ciemny motyw: `bg-gray-900` jako tło aplikacji, `bg-gray-800` dla paneli.
- ❌ Nie wyświetlaj modali na kliknięcie DPS —
  używaj **popupów Leaflet** osadzonych w mapie.

---

## Layout aplikacji

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER: [Logo] Inteligentna Mapa Woj. Lubelskiego  [Status][🎤]│
├──────────────────────────────────────────┬──────────────────────┤
│                                          │  PANEL BOCZNY (30%)  │
│                                          │  ┌────────────────┐  │
│                                          │  │ LayerControl   │  │
│                                          │  │ (toggle+czas)  │  │
│         MAPA (70%)                       │  └────────────────┘  │
│         React-Leaflet                    │  ┌────────────────┐  │
│         + warstwy GIS                    │  │ FilterPanel    │  │
│                                          │  │ (region/typ/   │  │
│                                          │  │  zagrożenie)   │  │
│   [Kliknięcie DPS → Popup Leaflet]       │  └────────────────┘  │
│                                          │  ┌────────────────┐  │
│                                          │  │ Top10Panel     │  │
│                                          │  │ (lista IKE)    │  │
│                                          │  └────────────────┘  │
│                                          │  ┌────────────────┐  │
│                                          │  │ RegionInfo     │  │
│                                          │  │ (po kliknięciu │  │
│                                          │  │  na powiat)    │  │
│                                          │  └────────────────┘  │
├──────────────────────────────────────────┴──────────────────────┤
│  [◀ Zwiń panel]  [🗺 Reset widoku]  [⊕ Kalkulatory]  [📡 Social]│
└─────────────────────────────────────────────────────────────────┘
```

**Zachowanie panelu bocznego:**
- Domyślnie rozwinięty po prawej stronie
- Przycisk `◀` zwija panel; mapa rozszerza się do 100% szerokości
- Po zwinięciu pasek dolny pokazuje przycisk `▶ Panel` do ponownego rozwinięcia
- Kalkulatory otwierają się jako **drawer** wysuwany od lewej, nakładający się na mapę
- Panel Social Media otwiera się jako **drawer** od lewej, pod kalkulatorami

**Popup DPS (Leaflet Popup — nie modal):**
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
| v1.0 — Fundament GIS | ⬜ Nie rozpoczęta | Mapa + granice + DPS-y + Spring Boot + PostGIS |
| v1.1 — Logika kryzysowa | ⬜ Nie rozpoczęta | 7 warstw + IKE + Top10 + trasy + WebSocket |
| v1.2 — Moduły dodatkowe | ⬜ Nie rozpoczęta | Scraper + 3 kalkulatory + PostGIS queries |
| v1.3 — AI & głos | ⬜ Nie rozpoczęta | Social media agent + asystent głosowy + Docker |

**Aktualna iteracja:** brak — projekt nie został jeszcze rozpoczęty.

> Aktualizuj tę tabelę po zakończeniu każdej iteracji:
> ⬜ Nie rozpoczęta → 🔄 W toku → ✅ Ukończona

---

## Jak uruchomić projekt lokalnie

```bash
# Skopiuj zmienne środowiskowe
cp .env.example .env
# Uzupełnij co najmniej POSTGRES_PASSWORD w .env

# --- TRYB DEV (zalecany do codziennej pracy) ---
# Uruchom tylko bazę danych
docker compose up -d postgres
# Następnie backend i frontend lokalnie — patrz docs/DEPLOYMENT.md sekcja 4

# --- TRYB FULL-STACK (demo / onboarding / VPS) ---
docker compose -f docker-compose.full.yml up --build
# Frontend: http://localhost:3000
# Backend:  http://localhost:8080
```

---

## Kluczowe pliki — gdzie co szukać

| Szukam... | Ścieżka |
|---|---|
| Konfiguracja warstw GIS | tabela `layer_config` w bazie — seed: `backend/src/main/resources/db/seed_layers.sql` |
| Wagi algorytmu IKE | `frontend/src/config/ike.config.json` (wczytywane przez backend) |
| Dane DPS-ów (seed SQL) | `backend/src/main/resources/db/seed_dps.sql` |
| Schematy tabel SQL | `backend/src/main/resources/db/schema.sql` + `docs/DATA_SCHEMA.md` |
| Algorytm IKE (opis + edge cases) | `docs/IKE_ALGORITHM.md` |
| Kontrakty API request/response | `docs/API_REFERENCE.md` |
| Główny komponent mapy | `frontend/src/components/map/MapContainer.jsx` |
| Logika IKE (Java) | `backend/src/main/java/pl/lublin/dashboard/service/IkeService.java` |
