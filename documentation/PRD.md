# PRD — Inteligentna Mapa Województwa Lubelskiego
## Geospatial Decision Dashboard — Moduł Ewakuacji Osób Zależnych

| | |
|---|---|
| **Projekt** | Inteligentna Mapa Województwa Lubelskiego |
| **Wersja** | 1.2 |
| **Status** | Draft |
| **Data** | 2026-04-14 |
| **Kontekst** | Urząd Marszałkowski Województwa Lubelskiego |

---

## 1. Cel produktu

Zbudowanie interaktywnego, webowego dashboardu geospatialnego dla Marszałka Województwa
Lubelskiego, który umożliwia podejmowanie decyzji opartych na danych w czasie rzeczywistym.

Moduł główny systemu: **zorganizowana, priorytetowa ewakuacja podopiecznych DPS-ów
i placówek opiekuńczych** w warunkach powodzi, pożaru lub blackoutu energetycznego.

### Problem

Dane kluczowe dla decyzji kryzysowych (lokalizacja i pojemność DPS-ów, dostępność
transportu, drożność dróg, zasięg zagrożenia) są rozproszone w kilkudziesięciu systemach
instytucjonalnych. Marszałek nie dysponuje centralnym widokiem ani narzędziem korelowania
tych informacji. W sytuacji kryzysowej ta fragmentacja bezpośrednio zagraża życiu osób
zależnych.

### Mierzalne cele sukcesu

Wyłącznie cele z bezpośrednim przełożeniem na wymagania implementacyjne:

| KPI | Cel | Gdzie weryfikować |
|---|---|---|
| Czas ładowania mapy przy pierwszym otwarciu | < 3 s przy łączu 10 Mbps | Test wydajnościowy Lighthouse |
| Czas odpowiedzi API (p95) | < 200 ms | Logi Spring Boot Actuator |
| Czas aktualizacji warstw live | < 5 min od zmiany w źródle | Test integracyjny WebSocket |
| Pokrycie placówek DPS w bazie | 100% DPS-ów województwa lubelskiego | Zapytanie `SELECT COUNT(*) FROM placowka` |
| Zmiana filtra / warstwy w UI | < 500 ms do odświeżenia widoku mapy | Test manualny |

---

## 2. Użytkownicy i persony

### 2.1 Persona główna — Marszałek / Operator Kryzysowy

- Osoba decyzyjna, nie-programista
- Pracuje na dużym monitorze lub tablecie w sali konferencyjnej
- Potrzebuje odpowiedzi na pytania: *„Które placówki ewakuować najpierw? Czym? Dokąd?"*
- Oczekuje danych widocznych na mapie bez klikania w spreadsheetach
- Może wydawać polecenia głosowo podczas briefingu (ręce zajęte, stres)

### 2.2 Persona pomocnicza — Koordynator Logistyczny

- Obsługuje system operacyjnie podczas kryzysu
- Nakłada i filtruje warstwy danych
- Korzysta z kalkulatorów zasobów do szybkiego oszacowania potrzeb transportowych
- Monitoruje social media pod kątem sygnałów z terenu

---

## 3. Zakres systemu

### 3.1 W zakresie (in-scope)

- Interaktywna mapa GIS województwa lubelskiego (podział na powiaty i gminy)
- Wielowarstwowe nakładki danych dla modułu ewakuacji osób zależnych
- Dynamiczne odświeżanie danych z timestampem ostatniej aktualizacji
- Filtry geograficzne (powiat / gmina) i tematyczne (typ zagrożenia, typ placówki)
- Responsywne UI na duży monitor / tablet
- Moduł integracji danych z publicznie dostępnych źródeł (web scraping)
- Wbudowane kalkulatory zasobów
- Agent social media (geolokalizacja sygnałów z terenu)
- Asystent głosowy (sterowanie mapą bez klawiatury)

### 3.2 Poza zakresem (out-of-scope)

- Integracja z systemami operacyjnymi służb ratunkowych (np. SWD PSP)
- Moduł autentykacji i zarządzania użytkownikami
- Obsługa danych niejawnych / wrażliwych (RODO)
- Wersja mobilna (smartfon)

> Baza danych PostgreSQL + PostGIS jest fundamentem systemu i jest **w zakresie**.
> Patrz `docs/ARCHITEKTURA_PLAN.md`.

---

## 4. Wymagania funkcjonalne

### 4.1 Wymagania podstawowe (obowiązkowe)

#### F-01 — Interaktywna mapa GIS

- Mapa województwa lubelskiego z podziałem administracyjnym: powiaty i gminy
  (213 gmin, 4 miasta na prawach powiatu)
- Płynny zoom od widoku województwa do poziomu konkretnej miejscowości
- Podkład mapowy OpenStreetMap
- Podświetlenie i zaznaczenie aktywnego powiatu / gminy po kliknięciu
- Wyświetlanie nazwy i podstawowych statystyk jednostki w panelu bocznym

#### F-02 — Wielowarstwowe nakładki danych

Użytkownik może niezależnie włączać, wyłączać i nakładać na siebie poniższe warstwy:

| ID | Nazwa | Typ geometrii | Źródło danych |
|---|---|---|---|
| L-01 | DPS i placówki opiekuńcze | Punkty | Tabela `placowka` |
| L-02 | Liczba i poziom samodzielności podopiecznych | Heatmapa | Tabela `placowka` |
| L-03 | Strefy zagrożenia (powódź / pożar / blackout) | Poligony | Tabela `strefy_zagrozen` |
| L-04 | Drożność dróg ewakuacyjnych | Linie | Tabela `drogi_ewakuacyjne` |
| L-05 | Dostępność transportu | Punkty | Tabela `zasob_transportu` |
| L-06 | Miejsca relokacji | Punkty | Tabela `miejsca_relokacji` |
| L-07 | „Białe plamy" transportowe | Poligony | Tabela `biale_plamy` |

#### F-03 — Dynamiczne odświeżanie danych

- Każda warstwa posiada widoczny znacznik czasu ostatniej aktualizacji
- Możliwość manualnego odświeżenia warstwy jednym kliknięciem
- Automatyczne odświeżanie co konfigurowalny interwał (domyślnie: 60 s dla warstw live,
  15 min dla danych rzadko zmieniających się)
- Wizualny wskaźnik stanu odświeżania (spinner / ikona statusu przy warstwie)

#### F-04 — Filtry i selekcja regionu

- Dropdown lub wyszukiwarka: filtrowanie widoku do wybranego powiatu lub gminy
- Filtr typologiczny: typ placówki (DPS, dom dziecka, hostel wspomagany itp.)
- Filtr poziomu zagrożenia: strefa czerwona / żółta / zielona
- Filtr krytyczności ewakuacji: wyliczany automatycznie przez IKE (patrz F-05)
- Możliwość łączenia filtrów (AND)
- Przycisk „Reset filtrów"

#### F-05 — Priorytetyzacja ewakuacji (Indeks Krytyczności Ewakuacji — IKE)

Kluczowa logika analityczna systemu. Dla każdej placówki backend wyznacza
**Indeks Krytyczności Ewakuacji (IKE)** — liczbę z zakresu [0.0–1.0].

Szczegółowa formuła, wagi i edge case'y: `docs/IKE_ALGORITHM.md`.

- Placówki na mapie kolorowane wg IKE: czerwony (≥ 0.70) / żółty (0.40–0.69) / zielony (< 0.40)
- Panel „Top 10 do ewakuacji" z listą priorytetową po boku mapy
- Po kliknięciu placówki: popup z danymi szczegółowymi + sugerowaną trasą ewakuacji
  + rekomendowanym miejscem relokacji
- Wizualizacja trasy ewakuacji jako linia na mapie z informacją o czasie przejazdu

#### F-06 — Responsywne UI

- Układ zoptymalizowany na rozdzielczość min. 1920×1080 i 1280×800 (tablet poziomo)
- Panel boczny z kontrolkami warstw i filtrami, zwijany do ikony
- Mapa zajmuje minimum 70% powierzchni ekranu
- Elementy UI czytelne z odległości 1–2 m od ekranu (min. 14px font dla etykiet)
- Obsługa dotyku (pinch-to-zoom, drag)

---

### 4.2 Wymagania dodatkowe

#### F-10 — Integracja danych z publicznie dostępnych źródeł

- Moduł automatycznie pobierający dane o placówkach i ich pojemności:
  - Rejestr Jednostek Pomocy Społecznej (mpips.gov.pl) — HTML
  - BIP urzędów powiatowych — HTML
  - Pliki XLSX/CSV publikowane przez Lubelski Urząd Marszałkowski
- Scraper uruchamiany manualnie (przycisk „Odśwież dane z urzędów") lub wg harmonogramu
- Log ostatniego scrapingu z listą pobranych rekordów i błędami
- Dane scrapowane zapisywane do bazy z adnotacją `zrodlo = 'scraping'`

#### F-11 — Wbudowane kalkulatory zasobów

**Kalkulator 1: Transport ewakuacyjny**
- Dane wejściowe: wybrana placówka lub strefa, promień poszukiwania pojazdów
- Wynik: szacowany czas ewakuacji, liczba kursów, lista sugerowanych pojazdów

**Kalkulator 2: Pojemność miejsc relokacji**
- Dane wejściowe: liczba ewakuowanych, poziom niesamodzielności
- Wynik: lista dostępnych miejsc relokacji z pojemnością i odległością,
  % wypełnienia po przyjęciu ewakuowanych

**Kalkulator 3: Zasięg zagrożenia w czasie**
- Dane wejściowe: typ zagrożenia, prędkość rozprzestrzeniania
- Wynik: szacowany czas do objęcia placówki zagrożeniem

UI: panel wysuwalny od lewej strony mapy; wyniki widoczne też jako warstwa na mapie.

#### F-12 — Agent social media

- Moduł analizujący posty zawierające geolokalizację lub nazwy miejscowości
  województwa lubelskiego
- Słowa kluczowe: powódź, pożar, blackout, ewakuacja, prąd, droga, zamknięte, pomoc
- Geolokalizacja postów: ekstrakcja toponimu → geokodowanie (Nominatim)
- Wyświetlanie jako pinezki na mapie z treścią i datą
- Panel „Ostatnie sygnały" z listą chronologiczną
- Dane demonstracyjne (seed w bazie) lub pobierane przez publiczne API

#### F-13 — Asystent głosowy

- Aktywacja: przycisk mikrofonu lub skrót klawiaturowy (Spacja)
- Obsługiwane komendy (język polski):

| Komenda (przykład) | Akcja |
|---|---|
| „Pokaż powiat lubelski" | Zoom do powiatu, podświetlenie |
| „Włącz warstwę zagrożeń" | Aktywacja warstwy L-03 |
| „Wyłącz transport" | Ukrycie warstwy L-05 |
| „Które placówki są czerwone?" | Filtr IKE = krytyczny, lista w panelu |
| „Pokaż trasę ewakuacji dla DPS Końskowola" | Wyświetlenie trasy na mapie |
| „Odśwież dane" | Manualne odświeżenie wszystkich warstw |
| „Ile miejsc relokacji w promieniu 30 km od Lublina?" | Uruchomienie kalkulatora |

- Feedback wizualny: animacja fali dźwiękowej podczas nasłuchiwania, transkrypcja tekstu
- Technologia: Web Speech API (przeglądarka), fallback: Whisper API (OpenAI)

---

## 5. Wymagania niefunkcjonalne

| ID | Wymaganie | Kryterium akceptacji |
|---|---|---|
| NF-01 | Wydajność ładowania mapy | Pierwsza mapa renderuje się < 3 s przy łączu 10 Mbps |
| NF-02 | Responsywność UI | Zmiana warstwy / filtra odpowiada < 500 ms |
| NF-03 | Przeglądarki | Chrome 120+, Edge 120+, Firefox 120+ |
| NF-04 | Czytelność | Kontrast spełnia WCAG AA dla kluczowych elementów UI |
| NF-05 | Skalowalność | Dodanie nowej warstwy GeoJSON wymaga zmiany tylko w tabeli `layer_config`, bez modyfikacji kodu |
| NF-06 | Czas odpowiedzi API | p95 < 200 ms dla endpointów REST (mierzony przez Spring Boot Actuator) |

---

## 6. Dane i źródła

### 6.1 Dane rzeczywiste / publiczne

| Dane | Źródło | Format |
|---|---|---|
| Granice administracyjne gmin i powiatów | GUGiK / GADM 4.1 | GeoJSON |
| Strefy zagrożenia powodziowego | ISOK / RZGW | GeoJSON / WMS |
| Rejestr placówek pomocy społecznej | mpips.gov.pl | HTML / CSV |
| Dane o drogach | OpenStreetMap (Overpass API) | GeoJSON |
| Dane demograficzne gmin | GUS | CSV/XLSX |

### 6.2 Dane seedowane do bazy

Dane startowe ładowane przez skrypty seed przy inicjalizacji bazy.
Nie są odczytywane z plików w runtime — żyją wyłącznie w PostgreSQL.

| Dane | Plik seed | Liczba rekordów |
|---|---|---|
| Placówki DPS | `seed_dps.sql` | 48 (po 2 na każdy z 24 powiatów) |
| Miejsca relokacji | `seed_relokacja.sql` | ~20 |
| Zasoby transportowe | `seed_transport.sql` | ~30 |
| Konfiguracja warstw | `seed_layers.sql` | 7 (L-01 … L-07) |
| Strefy zagrożeń | `seed_strefy.sql` | ~10 (syntetyczne) |
| Social media feed | `seed_social.sql` | ~25 (demonstracyjne) |

Dane syntetyczne używają prawdziwych nazw miejscowości lubelskich
i realistycznych wartości liczbowych.

---

## 7. Architektura techniczna

Szczegółowy opis w `docs/ARCHITEKTURA_PLAN.md`.

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
│  GeoController · IkeService · KalkulatorService      │
│  ScraperService · SocialMediaService · LiveFeedService│
└───────────────────────┬──────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────┐
│          PostgreSQL 15 + PostGIS                      │
│  Jedyne źródło danych runtime                        │
│  Geometrie · Warstwy · IKE results · Cache           │
└──────────────────────────────────────────────────────┘
```

### Decyzje technologiczne

| Warstwa | Technologia |
|---|---|
| Frontend | React 18 + Vite |
| Mapy | React-Leaflet (jedyna biblioteka map) |
| State management | Zustand |
| Data fetching | TanStack Query (React Query) |
| Backend | Spring Boot 3.x / OpenJDK 21 (LTS) |
| Live feed | WebSocket + STOMP (Spring native) |
| Baza danych | PostgreSQL 15 + PostGIS |
| Scraping HTML | Jsoup |
| Parsowanie XLSX | Apache POI |
| Routing tras | OSRM public API |
| Asystent głosowy | Web Speech API + fallback Whisper API (OpenAI) |
| Deploy | Docker + docker-compose |

---

## 8. User stories (MVP)

| ID | Jako… | Chcę… | Aby… |
|---|---|---|---|
| US-01 | Marszałek | zobaczyć mapę województwa z zaznaczonymi DPS-ami | wiedzieć, gdzie są placówki z osobami zależnymi |
| US-02 | Marszałek | włączyć warstwę zagrożenia powodziowego | zobaczyć, które placówki są w strefie zagrożenia |
| US-03 | Operator | kliknąć na placówkę | uzyskać dane o liczbie podopiecznych i rekomendowanej akcji |
| US-04 | Marszałek | zobaczyć listę „Top 10 do ewakuacji" | natychmiast wiedzieć, od czego zacząć |
| US-05 | Operator | uruchomić kalkulator transportu dla wybranego powiatu | szybko oszacować potrzeby pojazdów |
| US-06 | Marszałek | wydać komendę głosową „Pokaż powiat zamojski" | manewrować mapą bez odrywania rąk |
| US-07 | Operator | zobaczyć pinezki z social mediów | mieć sygnały z terenu poza oficjalnym kanałem |
| US-08 | Operator | odświeżyć dane z urzędowych stron jednym kliknięciem | mieć aktualne dane bez ręcznego przeglądania stron |
| US-09 | Operator | filtrować widok do wybranej gminy | skupić się na obszarze kryzysu |
| US-10 | Marszałek | zobaczyć trasę ewakuacji z DPS do miejsca relokacji | podjąć decyzję o kierunku transportu |

---

## 9. Kryteria akceptacji (Definition of Done)

- [ ] Mapa GIS ładuje się i wyświetla granice województwa z podziałem na powiaty i gminy
- [ ] Co najmniej 5 warstw tematycznych jest funkcjonalnych (włącz/wyłącz/nakładaj)
- [ ] Logika IKE wyznacza priorytety dla wszystkich placówek w bazie
- [ ] Panel „Top 10 do ewakuacji" wyświetla posortowaną listę z trasami na mapie
- [ ] Kalkulator transportu zwraca wynik dla wybranego obszaru
- [ ] Kalkulator miejsc relokacji działa i wyświetla wynik na mapie
- [ ] Scraper pobiera dane z co najmniej 1 publicznego źródła i aktualizuje bazę
- [ ] Social media agent wyświetla pinezki z postami geolokalizowanymi
- [ ] Asystent głosowy rozpoznaje co najmniej 7 komend z tabeli F-13
- [ ] UI renderuje się poprawnie na 1920×1080 i 1280×800
- [ ] Backend Spring Boot odpowiada na zapytania REST w < 200 ms (p95)
- [ ] WebSocket dostarcza aktualizacje warstw live bez przeładowania strony
- [ ] Kod źródłowy w repozytorium Git z dokumentacją uruchomienia

---

## 10. Plan iteracji

| Iteracja | Zakres |
|---|---|
| **v1.0 — Fundament GIS** | Mapa bazowa (React + React-Leaflet), warstwy L-01 i L-03, podział administracyjny, filtry geograficzne, Spring Boot z endpointami REST, PostgreSQL + PostGIS, seed 48 DPS-ów |
| **v1.1 — Logika kryzysowa** | Pozostałe warstwy (L-02, L-04, L-05, L-06, L-07), logika IKE, panel „Top 10", wizualizacja tras ewakuacji (OSRM), WebSocket live feed |
| **v1.2 — Moduły dodatkowe** | Scraper (Jsoup + Apache POI), 3 kalkulatory zasobów, zapytania PostGIS (`ST_DWithin`, `ST_Contains`) |
| **v1.3 — AI & głos** | Agent social media (geocoding toponimów), asystent głosowy (Web Speech API + Whisper fallback), testy wydajnościowe, dokumentacja wdrożeniowa |

---

## 11. Ryzyka

| Ryzyko | Prawdopodobieństwo | Wpływ | Mitygacja |
|---|---|---|---|
| Brak GeoJSON granic gmin w dobrej jakości | Niskie | Wysokie | GADM 4.1 jako źródło + instrukcja w `docs/DATA_SCHEMA.md` |
| Web Speech API niedokładne dla języka polskiego | Średnie | Średnie | Fallback Whisper API; tryb offline z predefiniowanymi przyciskami komend |
| Social media API zablokowane / płatne | Wysokie | Niskie | Seed demonstracyjny w bazie; w produkcji integracja przez oficjalne API |
| Publiczne strony urzędów zmieniają strukturę HTML | Średnie | Niskie | Selektory CSS w konfiguracji + cache ostatniego udanego pobrania w bazie |
| Wydajność mapy przy wielu warstwach jednocześnie | Średnie | Średnie | MarkerCluster, lazy loading warstw, zapytania PostGIS z bounding box |
