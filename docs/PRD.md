# PRD — Ogólnopolski Dashboard Jednostek Ochrony Ludności
## Geospatial Dashboard — Przegląd Zasobów Kryzysowych

| | |
|---|---|
| **Projekt** | Ogólnopolski Dashboard Jednostek Ochrony Ludności |
| **Wersja** | 1.3 |
| **Status** | Draft |
| **Data** | 2026-04-22 |
| **Kontekst** | Operatorzy kryzysowi / koordynatorzy zasobów |

---

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

---

## 2. Użytkownicy i persony

### 2.1 Persona główna — Operator Kryzysowy / Koordynator Zasobów

- Operator kryzysowy / koordynator zasobów na poziomie województwa lub kraju
- Pracuje na dużym monitorze lub tablecie podczas briefingu kryzysowego
- Potrzebuje odpowiedzi: *„Jakie jednostki ochrony ludności i zasoby są dostępne w zagrożonym obszarze?"*
- Oczekuje że alert zagrożenia automatycznie wskaże jednostki w zasięgu

### 2.2 Persona pomocnicza — Koordynator Logistyczny

- Obsługuje system operacyjnie podczas kryzysu
- Nakłada i filtruje warstwy, przegląda zasoby jednostek w obszarze alertu
- Weryfikuje listę jednostek w zasięgu i koordynuje działania

---

## 3. Zakres systemu

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

### 3.2 Poza zakresem (out-of-scope)

- Automatyczne rekomendacje ewakuacyjne (operator decyduje sam)
- Sztuczny scoring ryzyka (IKE)
- Integracja z systemami operacyjnymi służb (SWD PSP)
- Moduł autentykacji i zarządzania użytkownikami
- Obsługa danych niejawnych / wrażliwych (RODO)
- Wersja mobilna (smartfon)

---

## 4. Model działania systemu (event-driven)

System działa reaktywnie — alert zagrożenia automatycznie wskazuje jednostki
w zasięgu bez interwencji operatora.

### 4.1 Centralny event: `ThreatAlertEvent`

```
ThreatAlertImportAgent (@Scheduled co N minut lub HTTP POST /api/threats/manual)
    → poziom wody > próg alarmowy (IMGW API) lub ręczny trigger operatora
    → INSERT do threat_alert (is_active=true)
    → publishEvent(new ThreatAlertEvent(...))
    ↓ [HTTP odpowiada 202 Accepted — poniższe dzieje się asynchronicznie]
    ↓
    ├── NearbyUnitsAgent (@Async @EventListener ThreatAlertEvent)
    │       PostGIS ST_DWithin: entity_registry w radius_km od geom alertu
    │       publishEvent(NearbyUnitsComputedEvent)
    │            │
    │            └── LiveFeedService (@Async @EventListener NearbyUnitsComputedEvent)
    │                    pushuje /topic/nearby-units przez WebSocket
    │                    frontend automatycznie odświeża panel jednostek
    │
    └── LiveFeedService (@Async @EventListener ThreatAlertEvent)
            pushuje /topic/threat-alerts przez WebSocket

```

### 4.2 Źródła alertów zagrożeń

| Źródło | Trigger | Dane |
|---|---|---|
| IMGW API | `@Scheduled` co N minut | Poziomy wód — stacje hydrologiczne |
| Operator manualny | HTTP `POST /api/threats/manual` | Geolokalizacja + promień + typ |

---

## 5. Wymagania funkcjonalne

### 5.1 Wymagania podstawowe

#### F-01 — Interaktywna mapa GIS

- Mapa całej Polski z podziałem administracyjnym: województwa, powiaty, gminy
- Płynny zoom od widoku kraju do miejscowości
- Podkład OpenStreetMap
- Podświetlenie aktywnego powiatu / gminy po kliknięciu
- Statystyki jednostek w panelu bocznym

#### F-02 — Warstwy danych

| ID | Nazwa | Typ | Źródło |
|---|---|---|---|
| L-01 | Jednostki ochrony ludności (PSP, OSP, ZRM, Policja, WOT i inne) | Punkty | Tabela `entity_registry` |
| L-02 | Gęstość jednostek | Heatmapa | Tabela `entity_registry` |
| L-03 | Aktywne alerty zagrożeń | Poligony / punkty | Tabela `threat_alert` |
| L-04 | Granice administracyjne | Poligony | Tabela `admin_boundary` |
| L-05 | Zasięg alertu (jednostki w zasięgu) | Punkty | Wynik `NearbyUnitsAgent` |

#### F-03 — Dynamiczne odświeżanie

- Warstwy odświeżają się automatycznie po otrzymaniu eventu przez WebSocket
- Każda warstwa ma znacznik czasu ostatniej aktualizacji
- Wizualny wskaźnik stanu odświeżania (spinner przy warstwie)

#### F-04 — Filtry i selekcja regionu

- Filtrowanie widoku do województwa / powiatu / gminy
- Filtr: kategoria jednostki, typ zasobu, status alertu (w zasięgu / poza zasięgiem)
- Łączenie filtrów (AND), przycisk „Reset filtrów"

#### F-05 — Alerty zagrożeń (AlertsPanel)

- Lista aktywnych alertów z IMGW (poziomy wód) i alertów manualnych operatora
- Każdy alert: typ, lokalizacja, promień, czas aktywacji
- Kolorowanie markerów jednostek: czerwony (w zasięgu alertu) / zielony (poza zasięgiem)
- Przycisk „Wyzwól alert manualny" → `POST /api/threats/manual`
- Przycisk „Dezaktywuj alert" → `POST /api/threats/{id}/deactivate`

#### F-06 — Panel jednostek w zasięgu (NearbyUnitsPanel)

- Lista jednostek ochrony ludności w zasięgu aktywnego alertu (wynik `NearbyUnitsAgent`)
- Każda pozycja: nazwa, kategoria, odległość od alertu, dostępne zasoby
- Sortowanie po odległości / kategorii
- Kliknięcie → zoom na jednostkę na mapie

#### F-08 — Responsywne UI

- Min. 1920×1080 i 1280×800 (tablet poziomo)
- Mapa min. 70% ekranu, panel boczny zwijany
- Min. 14px font, obsługa dotyku

---

### 5.2 Wymagania dodatkowe

#### F-10 — Kalkulatory zasobów

**Kalkulator 1: Zasięg alertu**
- Wejście: punkt alertu, promień (km), typ zagrożenia
- Wynik: liczba i lista jednostek w zasięgu według kategorii

**Kalkulator 2: Dostępność zasobów**
- Wejście: typ zasobu (np. wóz cysternowy, ponton), obszar szukania
- Wynik: liczba dostępnych zasobów danego typu w zasięgu, lista jednostek posiadających zasób

**Kalkulator 3: Zasięg zagrożenia w czasie**
- Wejście: typ zagrożenia, prędkość rozprzestrzeniania, punkt startowy
- Wynik: szacowany czas do objęcia wskazanych jednostek zagrożeniem

#### F-11 — Import i scraping danych jednostek

- Pobieranie danych o jednostkach PSP z dane.gov.pl i BIP PSP
- Pobieranie danych PRM z rejestru RPWDL
- Ujednolicony rejestr podmiotów: tabela `entity_registry` + `entity_category` (kategoryzacja typów jednostek)
- Zapis do bazy z `zrodlo = 'scraping'` lub `zrodlo = 'api'`
- Log importu z liczbą rekordów i błędami (tabela `entity_import_batch`)

#### F-12 — Asystent głosowy

- Aktywacja: przycisk lub klawisz Spacja
- Obsługiwane komendy:

| Komenda | Akcja |
|---|---|
| „Pokaż powiat lubelski" | Zoom + podświetlenie powiatu |
| „Włącz warstwę zagrożeń" | Toggle L-03 |
| „Wyłącz transport" | Toggle L-05 |
| „Aktywuj powódź Q100" | Uruchomienie scenariusza powodziowego |
| „Które jednostki są w zasięgu alertu?" | Filtr status = w zasięgu |
| „Pokaż jednostki PSP w Lublinie" | Filtr kategoria=PSP + zoom |
| „Odśwież dane" | Manualne odświeżenie warstw |

- Technologia: Web Speech API + fallback Whisper API (OpenAI)

---

## 6. Wymagania niefunkcjonalne

| ID | Wymaganie | Kryterium |
|---|---|---|
| NF-01 | Ładowanie mapy | < 3 s przy 10 Mbps |
| NF-02 | Responsywność UI | < 500 ms na zmianę warstwy / filtra |
| NF-03 | Czas od alertu IMGW do aktualizacji mapy | < 30 s (polling + NearbyUnitsAgent + WebSocket) |
| NF-04 | Przeglądarki | Chrome 120+, Edge 120+, Firefox 120+ |
| NF-05 | Czytelność | Kontrast WCAG AA |
| NF-06 | Skalowalność | Nowa warstwa = INSERT do `layer_config`, zero zmian w kodzie |
| NF-07 | Asynchroniczność | `@Async` na listenerach — request HTTP kończy się przed przetworzeniem eventu |

---

## 7. Dane i źródła

### 7.1 Dane z zewnętrznych źródeł GIS

| Dane | Źródło | Protokół | Fallback |
|---|---|---|---|
| Poziomy wód — alerty | IMGW API | REST/JSON | Ręczny trigger operatora |
| Jednostki PSP | dane.gov.pl / PSP API | REST/JSON | Seed syntetyczny |
| Jednostki PRM | RPWDL | REST/JSON | Seed syntetyczny |
| Granice administracyjne | GUGiK PRG | WFS (GML) | GADM 4.1 GeoJSON |

### 7.2 Dane seedowane do bazy

| Dane | Plik seed | Rekordy |
|---|---|---|
| Jednostki ochrony ludności (demo) | `02_seed_entity_registry.sql` | 50+ (mix kategorii) |
| Typy zasobów | `02b_seed_resource_types.sql` | ~20 typów |
| Konfiguracja warstw | `03_seed_layers.sql` | 7 (L-01…L-07) |
| Alerty zagrożeń (demo) | `05_seed_threat_alerts.sql` | ~5 |

---

## 8. Architektura (skrót)

Szczegóły: `docs/ARCHITEKTURA_PLAN.md`.

```
Frontend (React)
    ↕ REST + WebSocket (STOMP)
Backend (Spring Boot)
    ├── Controllers  — HTTP endpoints
    ├── Agents       — ThreatAlertImportAgent, NearbyUnitsAgent
    ├── Events       — ThreatAlertEvent, NearbyUnitsComputedEvent
    └── Services     — GeoService, LiveFeedService, ImportService
    ↕
PostgreSQL + PostGIS  (jedyne źródło danych runtime)
```

---

## 9. User stories

| ID | Jako… | Chcę… | Aby… |
|---|---|---|---|
| US-01 | Operator kryzysowy | zobaczyć wszystkie jednostki ochrony ludności na mapie całej Polski | szybko ocenić dostępne zasoby w danym rejonie |
| US-02 | Operator kryzysowy | zobaczyć aktualizację mapy bez odświeżania strony | wiedzieć na bieżąco jak zmienia się sytuacja |
| US-03 | Operator kryzysowy | wyzwolić alert zagrożenia dla wybranego obszaru | system automatycznie wskazał jednostki w zasięgu alertu |
| US-04 | Operator kryzysowy | kliknąć na jednostkę | uzyskać dane kontaktowe, zasoby i status alertu |
| US-05 | Operator kryzysowy | filtrować jednostki po kategorii i typie zasobu | znaleźć jednostki o konkretnych możliwościach |
| US-06 | Koordynator logistyczny | zobaczyć listę jednostek w zasięgu alertu z odległościami | skoordynować działania w obszarze zagrożenia |
| US-07 | Operator kryzysowy | wydać komendę głosową | manewrować mapą bez odrywania rąk |
| US-08 | Operator kryzysowy | dezaktywować alert | zresetować system po zakończeniu kryzysu |

---

## 10. Kryteria akceptacji (Definition of Done)

- [ ] Mapa całej Polski z jednostkami ochrony ludności ładuje się w < 3 s
- [ ] Frontend otrzymuje aktualizację przez WebSocket bez ręcznego odświeżania
- [ ] Alert zagrożenia (manualny lub z IMGW) automatycznie wskazuje jednostki w zasięgu
- [ ] `NearbyUnitsAgent` przelicza jednostki w zasięgu po `ThreatAlertEvent` — wynik w < 30 s
- [ ] Panel jednostek w zasięgu wyświetla posortowaną listę z odległościami
- [ ] Import jednostek PSP z dane.gov.pl działa (100% pokrycia po imporcie)
- [ ] `POST /api/threats/manual` tworzy alert i wywołuje `ThreatAlertEvent`
- [ ] `POST /api/threats/{id}/deactivate` dezaktywuje alert i czyści warstwę
- [ ] Filtry po kategorii jednostki i typie zasobu działają < 500 ms
- [ ] Scraper / import pobiera dane z ≥1 publicznego źródła
- [ ] Asystent głosowy rozpoznaje 7 komend z tabeli F-12
- [ ] UI działa na 1920×1080 i 1280×800
- [ ] Backend odpowiada < 200 ms (p95)

---

## 11. Plan iteracji

| Iteracja | Zakres |
|---|---|
| **v1.0 — Fundament GIS** | Mapa, granice, entity registry, Spring Boot, PostGIS, seed jednostek, REST `/api/layers`, `/api/entities` |
| **v1.1 — Zasoby + Alerty** | `resource_type`, `entity_resources`, `threat_alert`, `ThreatAlertImportAgent` (IMGW), `NearbyUnitsAgent`, WebSocket, `AlertsPanel`, `NearbyUnitsPanel` |
| **v1.2 — Importy API** | Import PSP z dane.gov.pl, PRM z RPWDL, Nominatim geokodowanie, clustering na mapie |
| **v1.3 — UX i głos** | Asystent głosowy (Web Speech API + Whisper), pełny docker-compose, testy wydajnościowe |

---

## 12. Ryzyki

| Ryzyko | Mitygacja |
|---|---|
| IMGW API niedostępne lub zmienia schemat | Ręczny trigger operatora jako fallback; cache ostatniego stanu w bazie |
| dane.gov.pl / RPWDL API niedostępne podczas importu | Retry z backoff; seed syntetyczny jako fallback startowy |
| Asynchroniczne `@Async` listenery — trudniejsze debugowanie | Szczegółowe logowanie eventów; ID korelacji w każdym evencie |
| PostGIS zapytania wolne przy dużej liczbie jednostek | Indeksy GiST na kolumnach geometry; paginacja wyników API |
| Web Speech API nie działa w Firefox / bez HTTPS | Fallback Whisper API; przyciski predefiniowanych komend w UI |
| GeoJSON granic niedostępny | GADM 4.1 jako źródło z instrukcją pobierania w `docs/DATA_SCHEMA.md` |
