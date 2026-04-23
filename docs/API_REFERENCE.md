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
| 404 | `ENTITY_NOT_FOUND` | Jednostka nie istnieje |
| 404 | `LAYER_NOT_FOUND` | Warstwa nie istnieje |
| 409 | `IMPORT_IN_PROGRESS` | Import już trwa |
| 500 | `INTERNAL_ERROR` | Nieoczekiwany błąd serwera |
| 503 | `WFS_UNAVAILABLE` | WFS niedostępny — użyto fallbacku |
| 503 | `IMGW_UNAVAILABLE` | API IMGW niedostępne — użyto cache |

---

## Spis endpointów

| Metoda | Ścieżka | Opis | Iteracja |
|---|---|---|---|
| `GET` | `/api/layers` | Lista konfiguracji warstw | v1.0 |
| `GET` | `/api/layers/{id}` | Dane GeoJSON warstwy (L-01…L-10) | v1.0 |
| `POST` | `/api/admin-boundaries/import` | Import granic z GUGiK PRG WFS | v1.0 |
| `GET` | `/api/entity-resources` | Zasoby konkretnej jednostki | v1.1 |
| `GET` | `/api/resource-types` | Słownik typów zasobów | v1.1 |
| `GET` | `/api/threats/active` | Lista aktywnych alertów zagrożeń | v1.1 |
| `POST` | `/api/threats/manual` | Ręczne utworzenie alertu zagrożenia | v1.1 |
| `GET` | `/api/nearby-units` | Jednostki w zasięgu alertu | v1.1 |
| `POST` | `/api/scraper/run` | Uruchomienie scrapera | v1.2 |
| `GET` | `/api/scraper/log` | Log ostatniego scrapingu | v1.2 |

---

## Endpointy — szczegóły

---

### `GET /api/layers`

Lista konfiguracji wszystkich aktywnych warstw GIS.

**Response 200:**

```json
[
  {
    "id": "L-01",
    "nazwa": "Jednostki ochrony ludności",
    "komponent": "EntityLayer",
    "typ_geometrii": "Point",
    "domyslnie_wlaczona": true,
    "endpoint": "/api/layers/L-01",
    "interval_odswiezania_s": 900,
    "kolor_domyslny": "#3B82F6",
    "ikona": "building",
    "opis": "Lokalizacja jednostek ochrony ludności (PSP, OSP, GOPR itp.)",
    "ostatnia_aktualizacja": "2026-04-14T08:00:00Z",
    "status": "ok"
  }
]
```

`status`: `"ok"` | `"stale"` | `"unavailable"`

---

### `POST /api/admin-boundaries/import`

Uruchamia `AdminBoundaryImportAgent`: pobiera granice z GUGiK PRG WFS dla wszystkich
trzech poziomów (województwo, powiat, gmina) i zapisuje do tabeli `granice_administracyjne`.

**Operacja asynchroniczna** — zwraca `202 Accepted` natychmiast. Import trwa ~2–5 minut.

**Body:** brak

**Response 202 Accepted:**

```json
{
  "status": "started",
  "poziomy": ["wojewodztwo", "powiat", "gmina"],
  "correlation_id": "a1b2c3d4-..."
}
```

**Response 409 (import już trwa):**

```json
{
  "error": "Import granic jest już w toku",
  "code": "IMPORT_IN_PROGRESS",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `GET /api/layers/{id}`

Dane GeoJSON jednej warstwy. `{id}` = `L-01` … `L-10`.

**Query params — warstwy L-01…L-07 (opcjonalne):**

| Param | Typ | Opis |
|---|---|---|
| `powiat` | string | Filtruj do powiatu |
| `gmina` | string | Filtruj do gminy |
| `bbox` | string | `lon_min,lat_min,lon_max,lat_max` |

**Query params — warstwy granic administracyjnych L-08…L-10:**

| Param | Wymagane dla | Opis |
|---|---|---|
| `kod_woj` | L-09 (zalecane), L-10 (wymagane) | 2-znakowy kod TERYT województwa, np. `06` |
| `bbox` | L-10 (alternatywa dla `kod_woj`) | `lon_min,lat_min,lon_max,lat_max` |

> L-10 bez `kod_woj` ani `bbox` zwraca **400** `FILTER_REQUIRED`
> (zbyt duży zbiór danych — ~2477 geometrii bez uproszczenia).

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
        "kod": "JOL-CHE-001",
        "nazwa": "Jednostka Ochrony Ludności w Chełmie",
        "typ": "PSP",
        "powiat": "chelm",
        "gmina": "Chelm",
        "adres": "ul. Przykładowa 1, 22-100 Chełm",
        "telefon": "82-123-45-67"
      }
    }
  ]
}
```

---

### `GET /api/entity-resources?entityId={id}`

Zasoby konkretnej jednostki.

**Response 200:**

```json
{
  "entity_id": 42,
  "resources": [
    { "resource_type_code": "woz_cysternowy", "name": "Wóz cysternowy", "quantity": 2, "is_available": true },
    { "resource_type_code": "ponton_motorowy", "name": "Ponton motorowy", "quantity": 1, "is_available": true }
  ]
}
```

---

### `GET /api/resource-types`

Lista wszystkich typów zasobów (słownik).

**Response 200:**

```json
{
  "count": 15,
  "resource_types": [
    { "code": "woz_cysternowy", "name": "Wóz cysternowy GBA", "category": "pojazd_gasniczy", "unit_of_measure": "szt" }
  ]
}
```

---

### `GET /api/threats/active`

Lista aktywnych alertów zagrożeń.

**Response 200:**

```json
{
  "count": 2,
  "alerts": [
    {
      "id": 1,
      "source_api": "imgw_hydro",
      "threat_type": "flood",
      "level": "alarm",
      "value": 520.0,
      "threshold": 480.0,
      "unit": "cm",
      "lat": 51.24,
      "lon": 22.57,
      "radius_km": 30.0,
      "detected_at": "2026-04-22T10:00:00Z"
    }
  ]
}
```

---

### `POST /api/threats/manual`

Operator ręcznie tworzy alert zagrożenia.

**Request:**

```json
{
  "threat_type": "flood",
  "level": "warning",
  "lat": 51.2,
  "lon": 22.5,
  "radius_km": 25.0,
  "description": "Ręczny alert"
}
```

**Response 202:**

```json
{ "status": "started", "alert_id": 5, "correlation_id": "uuid" }
```

---

### `GET /api/nearby-units?alertId={id}`

Jednostki w zasięgu alertu (wynik NearbyUnitsAgent).

**Response 200:**

```json
{
  "alert_id": 5,
  "entity_ids": [12, 34, 67],
  "count": 3
}
```

---

### `POST /api/scraper/run`

**Body:** brak

**Response 202:**

```json
{
  "status": "started",
  "job_id": "scrape-2026-04-14-090000",
  "zrodla": ["bip.komendy-psp.gov.pl", "imgw.pl/api"],
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

Aplikacja działa event-driven — gdy pojawi się nowy alert zagrożenia (z IMGW lub ręczny),
backend uruchamia asynchroniczny łańcuch obliczania jednostek w zasięgu.
WebSocket (STOMP over SockJS) jest jedynym mechanizmem, który pozwala frontendowi
dowiedzieć się, że obliczenia się zakończyły i odświeżyć mapę
**bez poolingu i bez ręcznego odświeżenia strony przez operatora**.

Konkretne zastosowania w UI:

| Co się zmienia | Topik | Komponent który reaguje |
|---|---|---|
| Nowe alerty zagrożeń pojawiają się na mapie | `/topic/threat-alerts` | `AlertLayer.jsx` |
| Lista jednostek w zasięgu zaktualizowana | `/topic/nearby-units` | `EntityLayer.jsx`, `NearbyUnitsPanel.jsx` |
| Pasek statusu w nagłówku informuje o postępie | `/topic/system` | `Header.jsx` |

Frontend **nie używa WebSocket do wysyłania** — wyłącznie do odbierania. Wszystkie
akcje operatora idą przez REST (`POST /api/threats/manual` itp.).

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

#### `/topic/threat-alerts` — nowe alerty zagrożeń

Publikowany przez `LiveFeedService` po `ThreatAlertEvent`.

Frontend po otrzymaniu wykonuje `invalidateQueries(['threats', 'active'])` (React Query),
co powoduje ponowne pobranie listy alertów przez REST `GET /api/threats/active`.

```json
{
  "typ": "THREAT_ALERT_UPDATED",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-04-22T10:00:00Z",
  "alert_count": 2
}
```

---

#### `/topic/nearby-units` — jednostki w zasięgu alertu

Publikowany przez `LiveFeedService` po `NearbyUnitsComputedEvent`.

Niesie listę identyfikatorów jednostek w zasięgu alertu. Frontend aktualizuje
Zustand store bezpośrednio z payloadu.

```json
{
  "typ": "NEARBY_UNITS_COMPUTED",
  "alert_id": 5,
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "entity_ids": [12, 34, 67],
  "count": 3,
  "computed_at": "2026-04-22T10:00:15Z"
}
```

---

#### `/topic/system` — zdarzenia systemowe i postęp operacji

Używany przez `Header.jsx` do wyświetlania paska statusu podczas długich operacji
(import granic, obliczanie jednostek w zasięgu). Operator widzi co dzieje się "pod maską" bez
wchodzenia w logi.

```json
{
  "typ": "BOUNDARY_IMPORT_COMPLETED",
  "komunikat": "Import granic administracyjnych zakończony",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-04-22T10:00:45Z",
  "szczegoly": {
    "poziomy": ["wojewodztwo", "powiat", "gmina"]
  }
}
```

Pełna lista typów zdarzeń:

| `typ` | Publikuje | Znaczenie dla UI |
|---|---|---|
| `THREAT_ALERT_DETECTED` | `ThreatPollingAgent` | Nowy alert na mapie |
| `NEARBY_UNITS_STARTED` | `NearbyUnitsAgent` | Spinner na panelu jednostek |
| `NEARBY_UNITS_COMPUTED` | `LiveFeedService` | Lista jednostek zaktualizowana |
| `BOUNDARY_IMPORT_STARTED` | `AdminBoundaryImportAgent` | Spinner importu granic |
| `BOUNDARY_IMPORT_COMPLETED` | `LiveFeedService` | Granice załadowane |
| `SCRAPER_COMPLETED` | `LiveFeedService` | Toast: "Pobrano N rekordów" |
| `SYSTEM_WARNING` | dowolny agent | Toast ostrzeżenia (żółty) |

Struktura `szczegoly` jest opcjonalna i różni się zależnie od `typ` — frontend
powinien traktować ją jako `Map<String, Object>` i wyświetlać tylko `komunikat`.

---

## Mapowanie endpointów → komponenty React

| Endpoint | Komponent / Hook |
|---|---|
| `GET /api/layers` | `useLayerData.js`, `LayerControlPanel.jsx` |
| `GET /api/layers/{id}` | każdy `*Layer.jsx` |
| `POST /api/admin-boundaries/import` | przycisk w `LayerControlPanel.jsx` |
| `GET /api/entity-resources` | `EntityPopup.tsx` |
| `GET /api/resource-types` | `ResourcePanel.jsx` |
| `GET /api/threats/active` | `AlertLayer.jsx`, `AlertPanel.jsx` |
| `POST /api/threats/manual` | `ManualAlertPanel.jsx` |
| `GET /api/nearby-units` | `NearbyUnitsPanel.jsx`, `EntityLayer.jsx` |
| `POST /api/scraper/run` | przycisk w `LayerControlPanel.jsx` |
| WebSocket `/topic/threat-alerts` | `useWebSocket.js` → `invalidateQueries` → `AlertLayer.jsx` |
| WebSocket `/topic/nearby-units` | `useWebSocket.js` → Zustand store → `EntityLayer.jsx`, `NearbyUnitsPanel.jsx` |
| WebSocket `/topic/system` | `useWebSocket.js` → `Header.jsx` (pasek statusu + toasty) |
