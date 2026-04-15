# API_REFERENCE.md — Dokumentacja REST API

> Kontrakt między frontendem (React) a backendem (Spring Boot).
> **Czytaj przed implementacją kontrolerów Spring lub hooków React Query.**

---

## Konwencje ogólne

| Element | Wartość |
|---|---|
| Base URL (dev) | `http://localhost:8080` |
| Base URL (prod) | `VITE_API_BASE_URL` z `.env` |
| Format | JSON (`Content-Type: application/json`) |
| Autentykacja | Brak w v1.x |

### Format błędu (wszystkie endpointy)

```json
{
  "error": "Czytelny opis błędu",
  "code": "SNAKE_CASE_ERROR_CODE",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

| HTTP | `code` | Znaczenie |
|---|---|---|
| 400 | `INVALID_PARAMETER` | Nieprawidłowa wartość parametru |
| 404 | `PLACOWKA_NOT_FOUND` | Placówka nie istnieje |
| 404 | `LAYER_NOT_FOUND` | Warstwa nie istnieje |
| 409 | `IMPORT_IN_PROGRESS` | Import WFS już trwa |
| 409 | `RECALCULATE_IN_PROGRESS` | IKE już jest obliczane |
| 422 | `IKE_NULL_RESULT` | IKE nie może być obliczone |
| 500 | `INTERNAL_ERROR` | Nieoczekiwany błąd serwera |
| 503 | `WFS_UNAVAILABLE` | WFS niedostępny — użyto fallbacku |
| 503 | `OSRM_UNAVAILABLE` | OSRM niedostępny |

---

## Spis endpointów

| Metoda | Ścieżka | Opis | Iteracja |
|---|---|---|---|
| `POST` | `/api/threat/flood/import` | Import danych WFS + publikacja ThreatUpdatedEvent | v1.1 |
| `POST` | `/api/threat/clear` | Wyczyszczenie stref + publikacja ThreatUpdatedEvent | v1.1 |
| `GET` | `/api/layers` | Lista konfiguracji warstw | v1.0 |
| `GET` | `/api/layers/{id}` | Dane GeoJSON warstwy | v1.0 |
| `GET` | `/api/ike` | Wyniki IKE dla wszystkich placówek | v1.0 |
| `GET` | `/api/ike/{kod}` | Wynik IKE dla jednej placówki | v1.0 |
| `POST` | `/api/ike/recalculate` | Wymuszenie przeliczenia (admin) | v1.1 |
| `GET` | `/api/ike/config` | Aktualna konfiguracja wag IKE | v1.0 |
| `GET` | `/api/decisions` | Rekomendacje ewakuacyjne | v1.1 |
| `PATCH` | `/api/decisions/{id}` | Zatwierdzenie / odrzucenie rekomendacji | v1.1 |
| `POST` | `/api/calculate/transport` | Kalkulator transportu | v1.2 |
| `POST` | `/api/calculate/relocation` | Kalkulator miejsc relokacji | v1.2 |
| `POST` | `/api/calculate/threat` | Kalkulator zasięgu zagrożenia | v1.2 |
| `POST` | `/api/scraper/run` | Uruchomienie scrapera | v1.2 |
| `GET` | `/api/scraper/log` | Log ostatniego scrapingu | v1.2 |

---

## Endpointy — szczegóły

---

### `POST /api/threat/flood/import`

Uruchamia `FloodImportAgent`: pobiera dane z WFS ISOK lub generuje syntetyczne,
zapisuje do bazy i publikuje `ThreatUpdatedEvent`.

**Operacja asynchroniczna** — zwraca `202 Accepted` natychmiast.
Aktualizacja mapy następuje przez WebSocket (`/topic/layers/L-03`, `/topic/ike`).

**Body:**

```json
{
  "obszar": "chelm",
  "scenariusz": "Q100"
}
```

| Pole | Typ | Wymagane | Opis |
|---|---|---|---|
| `obszar` | string | ✅ | Kod powiatu (np. `chelm`, `lubelski`) lub bbox `lon_min,lat_min,lon_max,lat_max` |
| `scenariusz` | string | ✅ | `Q10` \| `Q100` \| `Q500` \| `pozar_maly` \| `pozar_sredni` \| `pozar_duzy` \| `blackout_powiat` |

**Response 202 Accepted:**

```json
{
  "status": "started",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "scenariusz": "Q100",
  "obszar": "chelm",
  "zrodlo_danych": "wfs",
  "szacowany_czas_s": 20,
  "websocket_topics": ["/topic/layers/L-03", "/topic/ike", "/topic/decisions"]
}
```

> Gdy WFS niedostępny, `zrodlo_danych` zwróci `"syntetyczne"` i pojawi się
> nagłówek odpowiedzi `X-Fallback-Used: true`.

**Response 409 (import już trwa):**

```json
{
  "error": "Import jest już w toku (correlation_id: ...)",
  "code": "IMPORT_IN_PROGRESS",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `POST /api/threat/clear`

Czyści wszystkie aktywne strefy zagrożeń z tabeli `strefy_zagrozen`
i publikuje `ThreatUpdatedEvent` z pustą listą stref (IKE = 0 dla wszystkich placówek).

**Body:** brak

**Response 202 Accepted:**

```json
{
  "status": "started",
  "correlation_id": "a1b2c3d4-...",
  "akcja": "clear",
  "usunieto_stref": 3,
  "websocket_topics": ["/topic/layers/L-03", "/topic/ike", "/topic/decisions"]
}
```

---

### `GET /api/layers`

Lista konfiguracji wszystkich aktywnych warstw GIS.

**Response 200:**

```json
[
  {
    "id": "L-01",
    "nazwa": "DPS i placówki opiekuńcze",
    "komponent": "DPSLayer",
    "typ_geometrii": "Point",
    "domyslnie_wlaczona": true,
    "endpoint": "/api/layers/L-01",
    "interval_odswiezania_s": 900,
    "kolor_domyslny": "#3B82F6",
    "ikona": "building",
    "opis": "Lokalizacja placówek DPS i domów opieki",
    "ostatnia_aktualizacja": "2026-04-14T08:00:00Z",
    "status": "ok"
  }
]
```

`status`: `"ok"` | `"stale"` | `"unavailable"`

---

### `GET /api/layers/{id}`

Dane GeoJSON jednej warstwy. `{id}` = `L-01` … `L-07`.

**Query params (opcjonalne):**

| Param | Typ | Opis |
|---|---|---|
| `powiat` | string | Filtruj do powiatu |
| `gmina` | string | Filtruj do gminy |
| `bbox` | string | `lon_min,lat_min,lon_max,lat_max` |

**Response 200 — warstwa L-01 (fragment):**

```json
{
  "type": "FeatureCollection",
  "layer_id": "L-01",
  "ostatnia_aktualizacja": "2026-04-14T08:00:00Z",
  "feature_count": 48,
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [23.4720, 51.1433] },
      "properties": {
        "kod": "DPS-CHE-001",
        "nazwa": "Dom Pomocy Społecznej przy ul. Polnej w Chełmie",
        "typ": "DPS_dorosli",
        "powiat": "chelm",
        "liczba_podopiecznych": 83,
        "pojemnosc_ogolna": 90,
        "niesamodzielni_procent": 0.71,
        "generator_backup": true,
        "ike_score": 0.8320,
        "ike_kategoria": "czerwony"
      }
    }
  ]
}
```

**Response 200 — warstwa L-03 (strefy zagrożeń, fragment):**

```json
{
  "type": "FeatureCollection",
  "layer_id": "L-03",
  "ostatnia_aktualizacja": "2026-04-14T09:05:00Z",
  "feature_count": 2,
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [[[23.35, 51.18],[23.42, 51.18],[23.42, 51.25],[23.35, 51.25],[23.35, 51.18]]]
      },
      "properties": {
        "id": "POWODZ-CHE-001",
        "typ_zagrozenia": "powodz",
        "poziom": "czerwony",
        "scenariusz": "Q100",
        "nazwa": "Strefa zalewowa rzeki Uherka — rejon Sawina",
        "szybkosc_wznoszenia_m_h": 0.25,
        "czas_do_zagrozenia_h": 3,
        "zrodlo": "wfs"
      }
    }
  ]
}
```

---

### `GET /api/ike`

Wyniki IKE posortowane malejąco po `ike_score`.

**Query params (opcjonalne):**

| Param | Typ | Opis |
|---|---|---|
| `limit` | integer | Ogranicz liczbę wyników (domyślnie: wszystkie) |
| `kategoria` | string | `czerwony` \| `zolty` \| `zielony` |
| `powiat` | string | Filtruj do powiatu |

**Response 200:**

```json
{
  "obliczone_o": "2026-04-14T09:00:00Z",
  "correlation_id": "f47ac10b-...",
  "liczba_wynikow": 2,
  "wyniki": [
    {
      "placowka_kod": "DPS-CHE-002",
      "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
      "powiat": "chelm",
      "lat": 51.2108,
      "lon": 23.3984,
      "liczba_podopiecznych": 55,
      "niesamodzielni_liczba": 49,
      "ike_score": 0.8900,
      "ike_kategoria": "czerwony",
      "skladowe": {
        "score_zagrozenia": 1.00,
        "score_niesamodzielnych": 0.89,
        "score_braku_transportu": 1.00,
        "score_braku_droznosci": 0.70,
        "score_odleglosci_relokacji": 0.25
      },
      "cel_relokacji": {
        "kod": "REL-CHE-001",
        "nazwa": "Hala Sportowa MOSiR Chełm",
        "odleglosc_km": 22.4,
        "pojemnosc_dostepna": 180,
        "przyjmuje_niesamodzielnych": true
      },
      "trasa_ewakuacji_geojson": {
        "type": "LineString",
        "coordinates": [[23.3984, 51.2108],[23.4350, 51.1800],[23.4720, 51.1433]]
      },
      "czas_przejazdu_min": 31,
      "data_warnings": [],
      "obliczone_o": "2026-04-14T09:00:00Z"
    }
  ]
}
```

---

### `GET /api/ike/{kod}`

Wynik IKE dla jednej placówki. Identyczna struktura jak jeden obiekt z `GET /api/ike`.

---

### `POST /api/ike/recalculate`

Endpoint administratorski — publikuje `ThreatUpdatedEvent` z bieżącymi strefami.
Normalnie IKE przelicza się automatycznie po imporcie zagrożeń.

**Body:** brak

**Response 202:**

```json
{
  "status": "started",
  "correlation_id": "b2c3d4e5-...",
  "szacowany_czas_s": 15
}
```

**Response 409:** `RECALCULATE_IN_PROGRESS`

---

### `GET /api/ike/config`

Aktualna konfiguracja wag algorytmu IKE.

**Response 200:**

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
  "promienie_km": { "transport_dostepny": 15, "miejsca_relokacji": 50 },
  "ostatnia_zmiana": "2026-04-14T08:00:00Z"
}
```

---

### `GET /api/decisions`

Rekomendacje ewakuacyjne wygenerowane przez `DecisionAgent` po ostatnim evencie.

**Query params (opcjonalne):**

| Param | Typ | Opis |
|---|---|---|
| `correlation_id` | string | Filtruj do konkretnego eventu |
| `rekomendacja` | string | `ewakuuj_natychmiast` \| `przygotuj_ewakuacje` \| `monitoruj` |
| `zatwierdzona` | boolean | `true` \| `false` \| brak (wszystkie) |

**Response 200:**

```json
{
  "correlation_id": "f47ac10b-...",
  "wygenerowano_o": "2026-04-14T09:01:30Z",
  "liczba_decyzji": 3,
  "decyzje": [
    {
      "id": 42,
      "placowka_kod": "DPS-CHE-002",
      "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
      "powiat": "chelm",
      "ike_score": 0.8900,
      "rekomendacja": "ewakuuj_natychmiast",
      "cel_relokacji_kod": "REL-CHE-001",
      "cel_relokacji_nazwa": "Hala Sportowa MOSiR Chełm",
      "uzasadnienie": "Placówka w strefie czerwonej, brak transportu w promieniu 15 km, 89% niesamodzielnych",
      "zatwierdzona": null,
      "wygenerowano_o": "2026-04-14T09:01:30Z"
    },
    {
      "id": 43,
      "placowka_kod": "DPS-CHE-001",
      "placowka_nazwa": "Dom Pomocy Społecznej przy ul. Polnej w Chełmie",
      "ike_score": 0.7320,
      "rekomendacja": "ewakuuj_natychmiast",
      "uzasadnienie": "IKE 0.73 — strefa żółta, wysoki udział niesamodzielnych",
      "zatwierdzona": true,
      "wygenerowano_o": "2026-04-14T09:01:30Z"
    }
  ]
}
```

---

### `PATCH /api/decisions/{id}`

Zatwierdzenie lub odrzucenie rekomendacji przez operatora.

**Body:**

```json
{ "zatwierdzona": true }
```

**Response 200:**

```json
{
  "id": 42,
  "placowka_kod": "DPS-CHE-002",
  "rekomendacja": "ewakuuj_natychmiast",
  "zatwierdzona": true,
  "zaktualizowano_o": "2026-04-14T09:05:00Z"
}
```

---

### `POST /api/calculate/transport`

**Body:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "promien_km": 30,
  "uwzgledniaj_tylko_przystosowane": true
}
```

**Response 200:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "podopieczni_do_ewakuacji": 55,
  "niesamodzielni_liczba": 49,
  "pojazdy_dostepne": [
    {
      "id": "TRP-005",
      "typ": "bus_sanitarny",
      "oznaczenie": "Bus San. CHE-1",
      "pojemnosc_osob": 8,
      "przyjmuje_niesamodzielnych": true,
      "odleglosc_km": 6.2
    }
  ],
  "szacunek": {
    "liczba_kursow_min": 7,
    "czas_ewakuacji_min": 210,
    "uwagi": "Niedobór pojazdów przystosowanych: 49 niesamodzielnych, dostępna pojemność: 8 miejsc/kurs"
  }
}
```

---

### `POST /api/calculate/relocation`

**Body:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "promien_km": 50,
  "tylko_dla_niesamodzielnych": false
}
```

**Response 200:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "podopieczni_do_przyjecia": 55,
  "miejsca_relokacji": [
    {
      "kod": "REL-CHE-001",
      "nazwa": "Hala Sportowa MOSiR Chełm",
      "odleglosc_km": 22.4,
      "pojemnosc_dostepna": 180,
      "przyjmuje_niesamodzielnych": true,
      "procent_wypelnienia_po_przyjeciu": 31
    }
  ],
  "lacznie_wolnych_miejsc": 180,
  "pokrycie": "wystarczajace"
}
```

`pokrycie`: `"wystarczajace"` | `"ograniczone"` | `"brak"`

---

### `POST /api/calculate/threat`

**Body:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "typ_zagrozenia": "powodz",
  "szybkosc_wznoszenia_m_h": 0.25
}
```

**Response 200:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "typ_zagrozenia": "powodz",
  "status_zagrozenia": "w_strefie",
  "czas_do_zagrozenia_h": null,
  "komentarz": "Placówka już w strefie zagrożenia powodziowego",
  "rekomendacja": "Natychmiastowa ewakuacja — IKE = 0.89"
}
```

`status_zagrozenia`: `"w_strefie"` | `"poza_strefa"` | `"brak_danych_strefy"`

---

### `POST /api/scraper/run`

**Body:** brak

**Response 202:**

```json
{
  "status": "started",
  "job_id": "scrape-2026-04-14-090000",
  "zrodla": ["mpips.gov.pl/rejestr-placowek"],
  "szacowany_czas_s": 120
}
```

---

### `GET /api/scraper/log`

**Response 200:**

```json
{
  "job_id": "scrape-2026-04-14-090000",
  "status": "completed",
  "rozpoczeto": "2026-04-14T09:00:00Z",
  "zakonczone": "2026-04-14T09:01:48Z",
  "wyniki": {
    "pobrano_rekordow": 34,
    "zaktualizowano": 12,
    "dodano_nowych": 2,
    "bledow": 1
  },
  "bledy": [
    {
      "zrodlo": "bip.powiat-chelm.pl",
      "komunikat": "HTTP 503 — strona niedostępna"
    }
  ]
}
```

---

## WebSocket — live feed

### Do czego służy WebSocket w tej aplikacji

Aplikacja działa event-driven — gdy operator aktywuje scenariusz zagrożenia, backend
uruchamia asynchroniczny łańcuch: import stref → obliczenie IKE → generowanie rekomendacji.
WebSocket (STOMP over SockJS) jest jedynym mechanizmem,
który pozwala frontendowi dowiedzieć się, że obliczenia się zakończyły i odświeżyć mapę
**bez poolingu i bez ręcznego odświeżenia strony przez operatora**.

Konkretne zastosowania w UI:

| Co się zmienia | Topik | Komponent który reaguje |
|---|---|---|
| Nowe strefy zagrożeń pojawiają się na mapie | `/topic/layers/L-03` | `ZagrozeniaLayer.jsx` |
| Markery DPS zmieniają kolor (IKE przeliczone) | `/topic/ike` | `DPSLayer.jsx`, `Top10Panel.jsx` |
| Rekomendacje pojawiają się w panelu | `/topic/decisions` | `DecisionPanel.jsx` |
| Pasek statusu w nagłówku informuje o postępie | `/topic/system` | `Header.jsx` |

Frontend **nie używa WebSocket do wysyłania** — wyłącznie do odbierania. Wszystkie
akcje operatora idą przez REST (`POST /api/threat/flood/import` itp.).

---

### Połączenie

**URL:** `ws://localhost:8080/ws` (dev) / `wss://{domena}/ws` (prod)  
**Protokół:** STOMP over SockJS  
**Autentykacja:** brak w v1.x

```javascript
// services/websocketService.js
const client = new Client({
  webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
  reconnectDelay: 5000,
});
```

---

### Topiki i payloady

#### `/topic/layers/L-03` — aktualizacja stref zagrożeń

Publikowany przez `LiveFeedService` po `ThreatUpdatedEvent` (gdy nowe strefy są już w bazie).

Frontend po otrzymaniu tego komunikatu wykonuje `invalidateQueries(['layers', 'L-03'])`
(React Query), co powoduje ponowne pobranie pełnego GeoJSON przez REST `GET /api/layers/L-03`.
WebSocket niesie sygnał, nie dane — dzięki temu payload jest lekki niezależnie od
liczby stref.

```json
{
  "typ": "LAYER_UPDATED",
  "layer_id": "L-03",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-04-14T09:00:45Z",
  "liczba_obiektow": 2,
  "scenariusz": "Q100",
  "obszar": "chelm"
}
```

---

#### `/topic/ike` — wyniki IKE zaktualizowane

Publikowany przez `LiveFeedService` po `IkeRecalculatedEvent`.

Niesie pełną listę wyników dla wszystkich placówek — frontend aktualizuje Zustand store
bezpośrednio z payloadu (bez dodatkowego REST call), bo dane są kompletne.

```json
{
  "typ": "IKE_RECALCULATED",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "obliczone_o": "2026-04-14T09:00:58Z",
  "statystyki": {
    "przetworzone": 48,
    "czerwonych": 3,
    "zoltych": 7,
    "zielonych": 36,
    "nieznanych": 2
  },
  "wyniki": [
    {
      "placowka_kod": "DPS-CHE-002",
      "lat": 51.2108,
      "lon": 23.3984,
      "ike_score": 0.8900,
      "ike_kategoria": "czerwony",
      "cel_relokacji_kod": "REL-CHE-001",
      "cel_relokacji_nazwa": "Hala Sportowa MOSiR Chełm",
      "czas_przejazdu_min": 31
    },
    {
      "placowka_kod": "DPS-LBL-001",
      "lat": 51.3012,
      "lon": 22.5891,
      "ike_score": 0.4800,
      "ike_kategoria": "zolty",
      "cel_relokacji_kod": "REL-LBL-001",
      "cel_relokacji_nazwa": "Hala Widowiskowo-Sportowa w Lublinie",
      "czas_przejazdu_min": 18
    },
    {
      "placowka_kod": "DPS-VLO-002",
      "lat": 51.0500,
      "lon": 23.8200,
      "ike_score": null,
      "ike_kategoria": "nieznany",
      "cel_relokacji_kod": null,
      "cel_relokacji_nazwa": null,
      "czas_przejazdu_min": null
    }
  ]
}
```

> Lista zawiera **wszystkie 48 placówek** — frontend nadpisuje cały store jedną operacją.
> Składowe `score_*` nie są przekazywane przez WebSocket — dostępne przez `GET /api/ike/{kod}`.

---

#### `/topic/decisions` — nowe rekomendacje ewakuacyjne

Publikowany przez `LiveFeedService` po zakończeniu pracy `DecisionAgent`
(po odczekaniu na zapis do `evacuation_decisions` lub przez dodatkowy event
`DecisionsGeneratedEvent` — do decyzji implementacyjnej).

Niesie listę rekomendacji dla bieżącego `correlation_id`. Frontend podmienia
zawartość `DecisionPanel` w całości.

```json
{
  "typ": "DECISIONS_GENERATED",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "wygenerowano_o": "2026-04-14T09:01:30Z",
  "liczba_decyzji": 10,
  "decyzje": [
    {
      "id": 42,
      "placowka_kod": "DPS-CHE-002",
      "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
      "powiat": "chelm",
      "ike_score": 0.8900,
      "ike_kategoria": "czerwony",
      "rekomendacja": "ewakuuj_natychmiast",
      "cel_relokacji_kod": "REL-CHE-001",
      "cel_relokacji_nazwa": "Hala Sportowa MOSiR Chełm",
      "uzasadnienie": "Strefa czerwona Q100, brak transportu w 15 km, 89% niesamodzielnych",
      "zatwierdzona": null
    },
    {
      "id": 43,
      "placowka_kod": "DPS-LBL-001",
      "placowka_nazwa": "Dom Pomocy Społecznej im. Jana Pawła II w Niemcach",
      "powiat": "lubelski",
      "ike_score": 0.4800,
      "ike_kategoria": "zolty",
      "rekomendacja": "przygotuj_ewakuacje",
      "cel_relokacji_kod": "REL-LBL-001",
      "cel_relokacji_nazwa": "Hala Widowiskowo-Sportowa w Lublinie",
      "uzasadnienie": "Strefa żółta, 2 pojazdy dostępne, drogi przejezdne",
      "zatwierdzona": null
    }
  ]
}
```

---

#### `/topic/system` — zdarzenia systemowe i postęp operacji

Używany przez `Header.jsx` do wyświetlania paska statusu podczas długich operacji
(import WFS, przeliczanie IKE). Operator widzi co dzieje się "pod maską" bez
wchodzenia w logi.

```json
{
  "typ": "THREAT_IMPORT_COMPLETED",
  "komunikat": "Import Q100 dla powiatu chełmskiego zakończony — 2 strefy załadowane",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-04-14T09:00:45Z",
  "szczegoly": {
    "zrodlo_danych": "wfs",
    "liczba_stref": 2,
    "scenariusz": "Q100",
    "obszar": "chelm"
  }
}
```

Pełna lista typów zdarzeń:

| `typ` | Publikuje | Znaczenie dla UI |
|---|---|---|
| `THREAT_IMPORT_STARTED` | `FloodImportAgent` | Spinner na przycisku "Aktywuj scenariusz" |
| `THREAT_IMPORT_COMPLETED` | `LiveFeedService` | Spinner znika, mapa odświeżona |
| `THREAT_IMPORT_FALLBACK` | `LiveFeedService` | Toast: "Użyto danych syntetycznych (WFS niedostępny)" |
| `THREAT_IMPORT_FAILED` | `LiveFeedService` | Toast błędu, import nieudany |
| `THREAT_CLEARED` | `LiveFeedService` | Strefy znikają z mapy, IKE zerowane |
| `IKE_RECALCULATION_STARTED` | `IkeAgent` | Spinner na panelu Top10 |
| `IKE_RECALCULATED` | `LiveFeedService` | Markery DPS zmieniają kolor |
| `DECISIONS_GENERATED` | `LiveFeedService` | DecisionPanel wypełnia się rekomendacjami |
| `SCRAPER_COMPLETED` | `LiveFeedService` | Toast: "Pobrano N rekordów" |
| `SYSTEM_WARNING` | dowolny agent | Toast ostrzeżenia (żółty) |

Struktura `szczegoly` jest opcjonalna i różni się zależnie od `typ` — frontend
powinien traktować ją jako `Map<String, Object>` i wyświetlać tylko `komunikat`.

---

## Mapowanie endpointów → komponenty React

| Endpoint | Komponent / Hook |
|---|---|
| `POST /api/threat/flood/import` | `ScenarioPanel.jsx` |
| `POST /api/threat/clear` | `ScenarioPanel.jsx` |
| `GET /api/layers` | `useLayerData.js`, `LayerControlPanel.jsx` |
| `GET /api/layers/{id}` | każdy `*Layer.jsx` |
| `GET /api/ike` | `Top10Panel.jsx`, `DPSLayer.jsx` |
| `GET /api/ike/{kod}` | `DPSPopup.jsx` |
| `GET /api/decisions` | `DecisionPanel.jsx` |
| `PATCH /api/decisions/{id}` | `DecisionPanel.jsx` (przycisk zatwierdź/odrzuć) |
| `POST /api/calculate/transport` | `TransportCalculator.jsx` |
| `POST /api/calculate/relocation` | `RelocationCalculator.jsx` |
| `POST /api/calculate/threat` | `ThreatSpreadCalculator.jsx` |
| `POST /api/scraper/run` | przycisk w `LayerControlPanel.jsx` |
| WebSocket `/topic/layers/L-03` | `useWebSocket.js` → `invalidateQueries` → `ZagrozeniaLayer.jsx` |
| WebSocket `/topic/ike` | `useWebSocket.js` → Zustand store → `DPSLayer.jsx`, `Top10Panel.jsx` |
| WebSocket `/topic/decisions` | `useWebSocket.js` → Zustand store → `DecisionPanel.jsx` |
| WebSocket `/topic/system` | `useWebSocket.js` → `Header.jsx` (pasek statusu + toasty) |
