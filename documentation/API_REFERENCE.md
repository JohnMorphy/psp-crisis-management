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

**URL:** `ws://localhost:8080/ws` (dev) / `wss://domena/ws` (prod)

| Topik | Kiedy | Payload |
|---|---|---|
| `/topic/layers/{id}` | Po aktualizacji warstwy | Pełny GeoJSON warstwy |
| `/topic/ike` | Po `IkeRecalculatedEvent` | Lista `IkeResult` |
| `/topic/decisions` | Po wygenerowaniu rekomendacji | Lista `EvacuationDecision` |
| `/topic/system` | Zdarzenia systemowe | Obiekt zdarzenia |

**Przykład payloadu `/topic/system`:**

```json
{
  "typ": "THREAT_IMPORT_COMPLETED",
  "komunikat": "Import Q100 dla powiatu chełmskiego zakończony — 2 strefy załadowane",
  "correlation_id": "f47ac10b-...",
  "timestamp": "2026-04-14T09:00:45Z",
  "zrodlo_danych": "wfs"
}
```

Typy zdarzeń systemowych:

| `typ` | Znaczenie |
|---|---|
| `THREAT_IMPORT_STARTED` | FloodImportAgent rozpoczął pobieranie |
| `THREAT_IMPORT_COMPLETED` | Import zakończony, strefy zapisane |
| `THREAT_IMPORT_FALLBACK` | WFS niedostępny, użyto danych syntetycznych |
| `THREAT_IMPORT_FAILED` | Import nieudany (błąd krytyczny) |
| `THREAT_CLEARED` | Strefy wyczyszczone |
| `IKE_RECALCULATED` | IkeAgent zakończył obliczenia |
| `DECISIONS_GENERATED` | DecisionAgent wygenerował rekomendacje |
| `SCRAPER_COMPLETED` | Scraper zakończył działanie |
| `SYSTEM_WARNING` | Ostrzeżenie systemowe |

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
| WebSocket `/topic/layers/{id}` | `useWebSocket.js` |
| WebSocket `/topic/ike` | `useWebSocket.js` → `Top10Panel` |
| WebSocket `/topic/decisions` | `useWebSocket.js` → `DecisionPanel` |
| WebSocket `/topic/system` | `Header.jsx` (status systemu) |
