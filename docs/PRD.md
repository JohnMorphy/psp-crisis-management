# PRD — Inteligentna Mapa Województwa Lubelskiego
## Geospatial Decision Dashboard — Moduł Ewakuacji Osób Zależnych

| | |
|---|---|
| **Projekt** | Inteligentna Mapa Województwa Lubelskiego |
| **Wersja** | 1.3 |
| **Status** | Draft |
| **Data** | 2026-04-14 |
| **Kontekst** | Urząd Marszałkowski Województwa Lubelskiego |

---

## 1. Cel produktu

Interaktywny, webowy dashboard geospatialny dla Marszałka Województwa Lubelskiego
umożliwiający podejmowanie decyzji opartych na danych w czasie rzeczywistym.

Moduł główny: **zorganizowana, priorytetowa ewakuacja podopiecznych DPS-ów
i placówek opiekuńczych** w warunkach powodzi, pożaru lub blackoutu energetycznego.

### Problem

Dane kluczowe dla decyzji kryzysowych są rozproszone w kilkudziesięciu systemach.
W sytuacji kryzysowej operator musi ręcznie zbierać informacje z wielu źródeł,
korelować je i dopiero wtedy podjąć decyzję. Ta fragmentacja kosztuje czas — a czas
w kryzysie bezpośrednio zagraża życiu osób zależnych.

### Mierzalne cele sukcesu

| KPI | Cel | Gdzie weryfikować |
|---|---|---|
| Czas ładowania mapy | < 3 s przy łączu 10 Mbps | Lighthouse |
| Czas odpowiedzi API (p95) | < 200 ms | Spring Boot Actuator |
| Czas od wyboru scenariusza do aktualizacji mapy | < 30 s | Test e2e (import WFS + IKE + WebSocket) |
| Pokrycie placówek DPS w bazie | 100% DPS-ów województwa | `SELECT COUNT(*) FROM placowka` |
| Zmiana warstwy / filtra w UI | < 500 ms | Test manualny |

---

## 2. Użytkownicy i persony

### 2.1 Persona główna — Marszałek / Operator Kryzysowy

- Osoba decyzyjna, nie-programista
- Pracuje na dużym monitorze lub tablecie w sali konferencyjnej
- Potrzebuje odpowiedzi: *„Które placówki ewakuować? Czym? Dokąd?"*
- Oczekuje że wybranie scenariusza zagrożenia automatycznie uruchomi analizę

### 2.2 Persona pomocnicza — Koordynator Logistyczny

- Obsługuje system operacyjnie podczas kryzysu
- Nakłada i filtruje warstwy, korzysta z kalkulatorów zasobów
- Weryfikuje i zatwierdza wygenerowane rekomendacje ewakuacyjne

---

## 3. Zakres systemu

### 3.1 W zakresie (in-scope)

- Interaktywna mapa GIS województwa lubelskiego (powiaty, gminy)
- Wielowarstwowe nakładki danych
- Dynamiczne odświeżanie danych (event-driven przez WebSocket)
- Integracja danych geospatialnych z zewnętrznych źródeł (WFS / GeoJSON)
- Automatyczne przeliczanie sytuacji kryzysowej (IKE) w reakcji na zmianę zagrożenia
- Generowanie rekomendacji ewakuacyjnych przez DecisionAgent
- Filtry geograficzne i tematyczne
- Responsywne UI na duży monitor / tablet
- Wbudowane kalkulatory zasobów
- Asystent głosowy (sterowanie mapą)

### 3.2 Poza zakresem (out-of-scope)

- Integracja z systemami operacyjnymi służb ratunkowych (SWD PSP)
- Moduł autentykacji i zarządzania użytkownikami
- Obsługa danych niejawnych / wrażliwych (RODO)
- Wersja mobilna (smartfon)
- Integracja z social media

> Baza danych PostgreSQL + PostGIS jest fundamentem systemu i jest **w zakresie**.

---

## 4. Model działania systemu (event-driven)

System działa reaktywnie — zmiana stanu zagrożenia automatycznie uruchamia
łańcuch analiz bez interwencji operatora.

### 4.1 Centralny event: `ThreatUpdatedEvent`

```
Użytkownik: wybiera scenariusz (np. powódź Q100 dla powiatu chełmskiego)
    ↓
FloodImportAgent:
    1. pobiera dane z WFS (ISOK/RZGW) lub generuje syntetyczne (fallback)
    2. konwertuje GML → GeoJSON, transformuje układ współrzędnych → EPSG:4326
    3. zapisuje strefy do tabeli strefy_zagrozen
    4. publishEvent(new ThreatUpdatedEvent(scenariusz, obszar, timestamp))
    ↓ [HTTP odpowiada 202 Accepted — poniższe dzieje się asynchronicznie]
    ↓
    ├── IkeAgent (@Async @EventListener ThreatUpdatedEvent)
    │       przelicza IKE dla wszystkich placówek
    │       zapisuje wyniki do ike_results
    │       publishEvent(IkeRecalculatedEvent)   ← sekwencja gwarantowana
    │            │
    │            ├── DecisionAgent (@Async @EventListener IkeRecalculatedEvent)
    │            │       generuje rekomendacje ewakuacyjne na podstawie gotowych wyników IKE
    │            │       zapisuje do tabeli evacuation_decisions
    │            │
    │            └── LiveFeedService (@Async @EventListener IkeRecalculatedEvent)
    │                    pushuje /topic/ike, /topic/decisions przez WebSocket
    │                    frontend automatycznie odświeża mapę i panele
    │
    └── LiveFeedService (@Async @EventListener ThreatUpdatedEvent)
            pushuje /topic/layers/L-03 (nowe strefy) i /topic/system (postęp)
```

### 4.2 Scenariusze zagrożeń

Użytkownik nie rysuje stref ręcznie. Wybiera gotowy scenariusz z listy:

| Typ | Scenariusze | Źródło danych |
|---|---|---|
| Powódź | Q10, Q100, Q500 | WFS ISOK / RZGW (fallback: syntetyczny) |
| Pożar | Mały / Średni / Duży (promieniowo od punktu) | Syntetyczny (parametryczny) |
| Blackout | Powiat / Strefa (obszar administracyjny) | Syntetyczny (obszar z bazy) |

### 4.3 Kiedy IKE się przelicza

IKE przelicza się **wyłącznie** w reakcji na `ThreatUpdatedEvent`. Nie ma przycisku
„Przelicz IKE" dostępnego wprost — endpoint `POST /api/ike/recalculate` istnieje
wyłącznie na potrzeby administratora i wewnętrznie publikuje `ThreatUpdatedEvent`.

`DecisionAgent` i `LiveFeedService` (push IKE + rekomendacji) reagują na
`IkeRecalculatedEvent` — event publikowany przez `IkeAgent` po zakończeniu
obliczeń. Gwarantuje to, że rekomendacje nigdy nie są generowane na podstawie
częściowych wyników IKE.

---

## 5. Wymagania funkcjonalne

### 5.1 Wymagania podstawowe

#### F-01 — Interaktywna mapa GIS

- Mapa województwa lubelskiego z podziałem: powiaty i gminy (213 gmin + 4 miasta)
- Płynny zoom od widoku województwa do miejscowości
- Podkład OpenStreetMap
- Podświetlenie aktywnego powiatu / gminy po kliknięciu
- Statystyki jednostki w panelu bocznym

#### F-02 — Warstwy danych

| ID | Nazwa | Typ | Źródło |
|---|---|---|---|
| L-01 | DPS i placówki opiekuńcze | Punkty | Tabela `placowka` |
| L-02 | Gęstość podopiecznych | Heatmapa | Tabela `placowka` |
| L-03 | Strefy zagrożenia | Poligony | Tabela `strefy_zagrozen` |
| L-04 | Drożność dróg ewakuacyjnych | Linie | Tabela `drogi_ewakuacyjne` |
| L-05 | Dostępność transportu | Punkty | Tabela `zasob_transportu` |
| L-06 | Miejsca relokacji | Punkty | Tabela `miejsca_relokacji` |
| L-07 | Białe plamy transportowe | Poligony | Tabela `biale_plamy` |

#### F-03 — Dynamiczne odświeżanie

- Warstwy odświeżają się automatycznie po otrzymaniu eventu przez WebSocket
- Każda warstwa ma znacznik czasu ostatniej aktualizacji
- Wizualny wskaźnik stanu odświeżania (spinner przy warstwie)

#### F-04 — Filtry i selekcja regionu

- Filtrowanie widoku do powiatu / gminy
- Filtr: typ placówki, poziom zagrożenia IKE (czerwony / żółty / zielony)
- Łączenie filtrów (AND), przycisk „Reset filtrów"

#### F-05 — IKE (Indeks Krytyczności Ewakuacji)

Szczegóły: `docs/IKE_ALGORITHM.md`.

- Automatyczne przeliczanie po `ThreatUpdatedEvent`
- Kolorowanie markerów: czerwony (≥0.70) / żółty (0.40–0.69) / zielony (<0.40)
- Panel „Top 10 do ewakuacji"
- Popup z danymi placówki + sugerowaną trasą + rekomendowanym miejscem relokacji

#### F-06 — Panel scenariuszy (ScenarioPanel)

- Lista dostępnych scenariuszy (powódź Q10/Q100/Q500, pożar, blackout)
- Wybór obszaru: dropdown powiatu lub bbox na mapie
- Przycisk „Aktywuj scenariusz" → wywołuje `POST /api/threat/flood/import`
- Przycisk „Wyczyść zagrożenie" → wywołuje `POST /api/threat/clear`
- Status importu: spinner podczas pobierania WFS, komunikat błędu gdy WFS niedostępny

#### F-07 — Panel rekomendacji (DecisionPanel)

- Lista rekomendacji wygenerowanych przez DecisionAgent po ostatnim `IkeRecalculatedEvent`
- Każda rekomendacja: placówka, akcja (ewakuuj / przygotuj / monitoruj),
  priorytet, sugerowany cel relokacji
- Możliwość zatwierdzenia / odrzucenia rekomendacji przez operatora

#### F-08 — Responsywne UI

- Min. 1920×1080 i 1280×800 (tablet poziomo)
- Mapa min. 70% ekranu, panel boczny zwijany
- Min. 14px font, obsługa dotyku

---

### 5.2 Wymagania dodatkowe

#### F-10 — Kalkulatory zasobów

**Kalkulator 1: Transport ewakuacyjny**
- Wejście: placówka, promień szukania pojazdów
- Wynik: czas ewakuacji, liczba kursów, lista pojazdów

**Kalkulator 2: Pojemność miejsc relokacji**
- Wejście: liczba ewakuowanych, poziom niesamodzielności
- Wynik: lista miejsc z pojemnością i odległością, % wypełnienia

**Kalkulator 3: Zasięg zagrożenia w czasie**
- Wejście: typ zagrożenia, prędkość rozprzestrzeniania
- Wynik: szacowany czas do objęcia placówki zagrożeniem

#### F-11 — Scraper danych urzędowych

- Pobieranie danych o placówkach z mpips.gov.pl i BIP powiatów
- Zapis do bazy z `zrodlo = 'scraping'`
- Log scrapingu z liczbą rekordów i błędami

#### F-12 — Asystent głosowy

- Aktywacja: przycisk lub klawisz Spacja
- Obsługiwane komendy:

| Komenda | Akcja |
|---|---|
| „Pokaż powiat lubelski" | Zoom + podświetlenie powiatu |
| „Włącz warstwę zagrożeń" | Toggle L-03 |
| „Wyłącz transport" | Toggle L-05 |
| „Aktywuj powódź Q100" | Uruchomienie scenariusza powodziowego |
| „Które placówki są czerwone?" | Filtr IKE = czerwony |
| „Pokaż trasę ewakuacji dla DPS Końskowola" | Trasa na mapie |
| „Odśwież dane" | Manualne odświeżenie warstw |

- Technologia: Web Speech API + fallback Whisper API (OpenAI)

---

## 6. Wymagania niefunkcjonalne

| ID | Wymaganie | Kryterium |
|---|---|---|
| NF-01 | Ładowanie mapy | < 3 s przy 10 Mbps |
| NF-02 | Responsywność UI | < 500 ms na zmianę warstwy / filtra |
| NF-03 | Czas od ThreatUpdatedEvent do aktualizacji mapy | < 30 s (import + IKE + WebSocket) |
| NF-04 | Przeglądarki | Chrome 120+, Edge 120+, Firefox 120+ |
| NF-05 | Czytelność | Kontrast WCAG AA |
| NF-06 | Skalowalność | Nowa warstwa = INSERT do `layer_config`, zero zmian w kodzie |
| NF-07 | Asynchroniczność | `@Async` na listenerach — request HTTP kończy się przed przetworzeniem eventu |

---

## 7. Dane i źródła

### 7.1 Dane z zewnętrznych źródeł GIS

| Dane | Źródło | Protokół | Fallback |
|---|---|---|---|
| Strefy zagrożenia powodziowego Q10/Q100/Q500 | ISOK / RZGW Hydroportal | WFS (GML) | Syntetyczny GeoJSON |
| Granice administracyjne | GADM 4.1 / GUGiK | GeoJSON | Bounding box województwa |
| Drogi ewakuacyjne | OpenStreetMap (Overpass API) | GeoJSON | Seed syntetyczny |

### 7.2 Dane seedowane do bazy

| Dane | Plik seed | Rekordy |
|---|---|---|
| Placówki DPS | `seed_dps.sql` | 48 (po 2 na powiat) |
| Miejsca relokacji | `seed_relokacja.sql` | ~10 |
| Zasoby transportowe | `seed_transport.sql` | ~10 |
| Konfiguracja warstw | `seed_layers.sql` | 7 (L-01…L-07) |
| Strefy zagrożeń (demo) | `seed_strefy.sql` | ~5 |

---

## 8. Architektura (skrót)

Szczegóły: `docs/ARCHITEKTURA_PLAN.md`.

```
Frontend (React)
    ↕ REST + WebSocket (STOMP)
Backend (Spring Boot)
    ├── Controllers  — HTTP endpoints
    ├── Agents       — IkeAgent, DecisionAgent, FloodImportAgent
    ├── Events       — ThreatUpdatedEvent, IkeRecalculatedEvent
    └── Services     — GeoService, KalkulatorService, ScraperService
    ↕
PostgreSQL + PostGIS  (jedyne źródło danych runtime)
```

---

## 9. User stories

| ID | Jako… | Chcę… | Aby… |
|---|---|---|---|
| US-01 | Marszałek | wybrać scenariusz „powódź Q100" dla powiatu chełmskiego | system automatycznie pobrał dane i pokazał zagrożone placówki |
| US-02 | Marszałek | zobaczyć aktualizację mapy bez odświeżania strony | wiedzieć na bieżąco jak zmienia się sytuacja |
| US-03 | Operator | zobaczyć listę „Top 10 do ewakuacji" | natychmiast wiedzieć od czego zacząć |
| US-04 | Operator | kliknąć na placówkę | uzyskać dane o podopiecznych, IKE i rekomendowanej trasie |
| US-05 | Operator | zobaczyć panel rekomendacji | mieć gotowe decyzje do zatwierdzenia |
| US-06 | Operator | uruchomić kalkulator transportu | oszacować potrzeby pojazdów |
| US-07 | Marszałek | wydać komendę głosową | manewrować mapą bez odrywania rąk |
| US-08 | Operator | wyczyścić aktualne zagrożenie | zresetować system po zakończeniu kryzysu |

---

## 10. Kryteria akceptacji (Definition of Done)

- [ ] Wybór scenariusza zagrożenia uruchamia import danych i przeliczenie IKE
- [ ] Frontend otrzymuje aktualizację przez WebSocket bez ręcznego odświeżania
- [ ] IKE przelicza się automatycznie po `ThreatUpdatedEvent` — nie wymaga kliknięcia
- [ ] DecisionAgent generuje rekomendacje widoczne w DecisionPanel
- [ ] Panel „Top 10 do ewakuacji" wyświetla posortowaną listę z trasami
- [ ] FloodImportAgent działa z WFS i z fallbackiem syntetycznym
- [ ] `POST /api/threat/clear` czyści strefy i aktualizuje mapę
- [ ] Kalkulatory transportu, relokacji i zasięgu zagrożenia działają
- [ ] Scraper pobiera dane z ≥1 publicznego źródła
- [ ] Asystent głosowy rozpoznaje 7 komend z tabeli F-12
- [ ] UI działa na 1920×1080 i 1280×800
- [ ] Backend odpowiada < 200 ms (p95)

---

## 11. Plan iteracji

| Iteracja | Zakres |
|---|---|
| **v1.0 — Fundament GIS** | Mapa, granice, DPS-y, Spring Boot, PostGIS, seed 48 placówek, REST `/api/layers`, `/api/ike` |
| **v1.1 — Event-driven core** | `ThreatUpdatedEvent`, `IkeAgent`, `DecisionAgent`, `LiveFeedService`, WebSocket, `ScenarioPanel`, `DecisionPanel` |
| **v1.2 — Import i kalkulatory** | `FloodImportAgent` (WFS + fallback), `POST /api/threat/flood/import`, `POST /api/threat/clear`, 3 kalkulatory, Scraper |
| **v1.3 — UX i głos** | Asystent głosowy (Web Speech API + Whisper), pełny docker-compose, testy wydajnościowe |

---

## 12. Ryzyki

| Ryzyko | Mitygacja |
|---|---|
| WFS ISOK niedostępny lub zmienia schemat | Syntetyczny fallback; cache ostatniego udanego importu w bazie |
| Asynchroniczne `@Async` listenery — trudniejsze debugowanie | Szczegółowe logowanie eventów; ID korelacji w każdym evencie |
| PostGIS zapytania wolne przy 48 placówkach × 5 stref | Indeksy GiST; cache wyników IKE w `ike_results`; batch processing |
| Web Speech API nie działa w Firefox / bez HTTPS | Fallback Whisper API; przyciski predefiniowanych komend w UI |
| GeoJSON granic niedostępny | GADM 4.1 jako źródło z instrukcją pobierania w `docs/DATA_SCHEMA.md` |
