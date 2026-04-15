# API_REFERENCE.md — Dokumentacja REST API

> Kontrakt między frontendem (React) a backendem (Spring Boot).
> Każdy endpoint zawiera: metodę HTTP, ścieżkę, parametry, przykład request i response.
> **Czytaj przed implementacją kontrolerów Spring lub hooków React Query.**

---

## Konwencje ogólne

| Element | Wartość |
|---|---|
| Base URL (dev) | `http://localhost:8080` |
| Base URL (prod) | konfigurowany w `.env` jako `VITE_API_BASE_URL` |
| Format danych | JSON (`Content-Type: application/json`) |
| Kodowanie | UTF-8 |
| Autentykacja | Brak w v1.x (system wewnętrzny) |
| Wersjonowanie | Brak w v1.x — ścieżki bez prefiksu `/v1/` |

### Format błędu — wszystkie endpointy

Każdy błąd zwraca ten sam kształt JSON niezależnie od kodu HTTP:

```json
{
  "error": "Czytelny opis błędu po polsku",
  "code": "SNAKE_CASE_ERROR_CODE",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

Kody błędów używane w systemie:

| Kod HTTP | `code` | Znaczenie |
|---|---|---|
| 400 | `INVALID_PARAMETER` | Nieprawidłowa wartość parametru |
| 404 | `PLACOWKA_NOT_FOUND` | Placówka o podanym kodzie nie istnieje |
| 404 | `LAYER_NOT_FOUND` | Warstwa o podanym ID nie istnieje |
| 409 | `RECALCULATE_IN_PROGRESS` | IKE już jest obliczane — odrzuć duplikat |
| 422 | `IKE_NULL_RESULT` | IKE nie może być obliczone dla tej placówki |
| 500 | `INTERNAL_ERROR` | Nieoczekiwany błąd serwera |
| 503 | `OSRM_UNAVAILABLE` | OSRM niedostępny — trasy ewakuacji tymczasowo wyłączone |
| 503 | `LAYER_UNAVAILABLE` | Źródło danych warstwy tymczasowo niedostępne |

### Format timestampów

Wszystkie pola czasu w formacie ISO 8601 z UTC:
`"2026-04-14T09:23:00Z"`

---

## Spis endpointów

| Metoda | Ścieżka | Opis | Iteracja |
|---|---|---|---|
| `GET` | `/api/layers` | Lista konfiguracji wszystkich warstw | v1.0 |
| `GET` | `/api/layers/{id}` | Dane GeoJSON jednej warstwy | v1.0 |
| `GET` | `/api/ike` | Wyniki IKE dla wszystkich placówek | v1.1 |
| `GET` | `/api/ike/{kod}` | Wynik IKE dla jednej placówki | v1.1 |
| `POST` | `/api/ike/recalculate` | Wymuszenie przeliczenia IKE | v1.1 |
| `GET` | `/api/ike/config` | Aktualna konfiguracja wag IKE | v1.1 |
| `POST` | `/api/calculate/transport` | Kalkulator transportu ewakuacyjnego | v1.2 |
| `POST` | `/api/calculate/relocation` | Kalkulator pojemności miejsc relokacji | v1.2 |
| `POST` | `/api/calculate/threat` | Kalkulator zasięgu zagrożenia w czasie | v1.2 |
| `POST` | `/api/scraper/run` | Uruchomienie scrapera danych z urzędów | v1.2 |
| `GET` | `/api/scraper/log` | Log ostatniego scrapingu | v1.2 |
| `GET` | `/api/social/feed` | Feed sygnałów z social mediów | v1.3 |

---

## Endpointy — szczegóły

---

### `GET /api/layers`

Zwraca listę konfiguracji wszystkich warstw GIS.
Frontend używa tego do inicjalizacji `LayerManager.jsx` i `LayerControlPanel.jsx`.

**Parametry:** brak

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
    "opis": "Lokalizacja placówek DPS i domów opieki w województwie lubelskim",
    "ostatnia_aktualizacja": "2026-04-14T08:00:00Z",
    "status": "ok"
  },
  {
    "id": "L-03",
    "nazwa": "Strefy zagrożenia",
    "komponent": "ZagrozeniaLayer",
    "typ_geometrii": "Polygon",
    "domyslnie_wlaczona": true,
    "endpoint": "/api/layers/L-03",
    "interval_odswiezania_s": 300,
    "kolor_domyslny": "#EF4444",
    "ikona": "alert-triangle",
    "opis": "Strefy zagrożenia: powódź, pożar, blackout",
    "ostatnia_aktualizacja": "2026-04-14T09:05:00Z",
    "status": "ok"
  }
]
```

Pole `status` dla każdej warstwy:

| Wartość | Znaczenie |
|---|---|
| `"ok"` | Dane dostępne i świeże |
| `"stale"` | Dane starsze niż 2× `interval_odswiezania_s` |
| `"unavailable"` | Źródło niedostępne, serwowane z cache |

---

### `GET /api/layers/{id}`

Zwraca dane GeoJSON jednej warstwy. `{id}` to identyfikator warstwy np. `L-01`, `L-03`.

**Parametry ścieżki:**

| Parametr | Typ | Opis |
|---|---|---|
| `id` | string | ID warstwy: `L-01` … `L-07` |

**Parametry query (opcjonalne):**

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `powiat` | string | — | Filtruj features do podanego powiatu |
| `gmina` | string | — | Filtruj features do podanej gminy |
| `bbox` | string | — | Bounding box `lon_min,lat_min,lon_max,lat_max` |

**Przykład żądania:**
```
GET /api/layers/L-01?powiat=chelm
```

**Response 200 — warstwa L-01 (DPS, punkty):**

```json
{
  "type": "FeatureCollection",
  "layer_id": "L-01",
  "ostatnia_aktualizacja": "2026-04-14T08:00:00Z",
  "feature_count": 4,
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Point",
        "coordinates": [23.4720, 51.1433]
      },
      "properties": {
        "kod": "DPS-CHE-001",
        "nazwa": "Dom Pomocy Społecznej przy ul. Polnej w Chełmie",
        "typ": "DPS_dorosli",
        "powiat": "chelm",
        "gmina": "Chełm",
        "liczba_podopiecznych": 83,
        "pojemnosc_ogolna": 90,
        "niesamodzielni_procent": 0.71,
        "generator_backup": true,
        "personel_dyzurny": 9,
        "kontakt": "82-565-13-40",
        "ike_score": 0.7320,
        "ike_kategoria": "czerwony"
      }
    }
  ]
}
```

**Response 200 — warstwa L-03 (zagrożenia, poligony):**

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
        "coordinates": [[[23.35, 51.18], [23.42, 51.18], [23.42, 51.25], [23.35, 51.25], [23.35, 51.18]]]
      },
      "properties": {
        "id": "POWODZ-CHE-001",
        "typ_zagrozenia": "powodz",
        "poziom": "czerwony",
        "nazwa": "Strefa zalewowa rzeki Uherka — rejon Sawina",
        "szybkosc_wznoszenia_m_h": 0.25,
        "czas_do_zagrozenia_h": 3,
        "zrodlo": "syntetyczne"
      }
    }
  ]
}
```

**Response 404:**
```json
{
  "error": "Warstwa o identyfikatorze 'L-99' nie istnieje",
  "code": "LAYER_NOT_FOUND",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `GET /api/ike`

Zwraca wyniki IKE dla wszystkich placówek posortowane malejąco po `ike_score`.
Używany przez `Top10Panel.jsx` i `DPSLayer.jsx` do kolorowania markerów.

**Parametry query (opcjonalne):**

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `limit` | integer | `null` (wszystkie) | Ogranicz liczbę wyników |
| `kategoria` | string | — | Filtruj: `czerwony`, `zolty`, `zielony` |
| `powiat` | string | — | Filtruj do powiatu |

**Przykład żądania:**
```
GET /api/ike?limit=10&kategoria=czerwony
```

**Response 200:**

```json
{
  "obliczone_o": "2026-04-14T09:00:00Z",
  "liczba_wynikow": 3,
  "wyniki": [
    {
      "placowka_kod": "DPS-CHE-002",
      "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
      "powiat": "chelm",
      "gmina": "Sawin",
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
        "coordinates": [[23.3984, 51.2108], [23.4350, 51.1800], [23.4720, 51.1433]]
      },
      "czas_przejazdu_min": 31,
      "data_warnings": [],
      "obliczone_o": "2026-04-14T09:00:00Z"
    },
    {
      "placowka_kod": "DPS-VLO-002",
      "placowka_nazwa": "Dom Pomocy Społecznej w Hannie",
      "powiat": "wlodawski",
      "gmina": "Hanna",
      "lat": 51.6801,
      "lon": 23.6530,
      "liczba_podopiecznych": 28,
      "niesamodzielni_liczba": 24,
      "ike_score": null,
      "ike_kategoria": "nieznany",
      "skladowe": null,
      "cel_relokacji": null,
      "trasa_ewakuacji_geojson": null,
      "czas_przejazdu_min": null,
      "data_warnings": ["brak_miejsca_relokacji_w_promieniu_50km"],
      "obliczone_o": "2026-04-14T09:00:00Z"
    }
  ]
}
```

---

### `GET /api/ike/{kod}`

Wynik IKE dla jednej placówki. Używany przez `DPSPopup.jsx` po kliknięciu markera.

**Parametry ścieżki:**

| Parametr | Typ | Opis |
|---|---|---|
| `kod` | string | Kod placówki np. `DPS-CHE-002` |

**Response 200:** identyczna struktura jak pojedynczy obiekt z `GET /api/ike`.

**Response 404:**
```json
{
  "error": "Placówka o kodzie 'DPS-XXX-999' nie istnieje",
  "code": "PLACOWKA_NOT_FOUND",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `POST /api/ike/recalculate`

Wymusza pełne przeliczenie IKE dla wszystkich placówek.
Operacja asynchroniczna — zwraca natychmiast, obliczenia trwają w tle.

**Body:** brak (puste)

**Response 202 Accepted:**

```json
{
  "status": "started",
  "szacowany_czas_s": 15,
  "poprzednie_obliczenie": "2026-04-14T08:45:00Z"
}
```

**Response 409 (obliczanie już trwa):**
```json
{
  "error": "Przeliczanie IKE jest już w toku. Poczekaj na zakończenie.",
  "code": "RECALCULATE_IN_PROGRESS",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `GET /api/ike/config`

Zwraca aktualną konfigurację wag algorytmu IKE.
Używany przez `IKEScore.jsx` do wyświetlenia legendy w UI.

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
  "progi": {
    "czerwony": 0.70,
    "zolty": 0.40
  },
  "promienie_km": {
    "transport_dostepny": 15,
    "miejsca_relokacji": 50
  },
  "ostatnia_zmiana": "2026-04-14T08:00:00Z"
}
```

---

### `POST /api/calculate/transport`

Kalkulator transportu ewakuacyjnego.
Odpowiada na pytanie: „Ile pojazdów i kursów potrzeba do ewakuacji tej placówki?"

**Body:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "promien_km": 30,
  "uwzgledniaj_tylko_przystosowane": true
}
```

| Pole | Typ | Wymagane | Opis |
|---|---|---|---|
| `placowka_kod` | string | ✅ | Kod ewakuowanej placówki |
| `promien_km` | integer | ✅ | Promień poszukiwania pojazdów (1–100 km) |
| `uwzgledniaj_tylko_przystosowane` | boolean | ✅ | Tylko pojazdy z rampą/noszami |

**Response 200:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
  "podopieczni_do_ewakuacji": 55,
  "niesamodzielni_liczba": 49,
  "pojazdy_dostepne": [
    {
      "id": "TRP-045",
      "typ": "bus_sanitarny",
      "oznaczenie": "Bus San. CHE-3",
      "operator": "Pogotowie Chełm",
      "pojemnosc_osob": 10,
      "przyjmuje_niesamodzielnych": true,
      "odleglosc_km": 18.3
    },
    {
      "id": "TRP-022",
      "typ": "bus_zwykly",
      "oznaczenie": "Bus PKS ZAM-7",
      "operator": "PKS Zamość",
      "pojemnosc_osob": 45,
      "przyjmuje_niesamodzielnych": false,
      "odleglosc_km": 27.1
    }
  ],
  "szacunek": {
    "liczba_kursow_min": 6,
    "czas_ewakuacji_min": 185,
    "pojazdy_potrzebne_lacznie": 2,
    "uwagi": "Niedobór pojazdów przystosowanych: 49 niesamodzielnych, dostępna pojemność przystosowana: 10 miejsc/kurs"
  },
  "promien_km_uzyta": 30
}
```

**Response 400 (błędne dane):**
```json
{
  "error": "Parametr 'promien_km' musi być liczbą całkowitą z zakresu 1–100",
  "code": "INVALID_PARAMETER",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `POST /api/calculate/relocation`

Kalkulator pojemności miejsc relokacji.
Odpowiada na pytanie: „Gdzie możemy przyjąć ewakuowanych i ile wolnych miejsc zostanie?"

**Body:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "promien_km": 50,
  "tylko_dla_niesamodzielnych": false
}
```

| Pole | Typ | Wymagane | Opis |
|---|---|---|---|
| `placowka_kod` | string | ✅ | Kod ewakuowanej placówki |
| `promien_km` | integer | ✅ | Promień poszukiwania (1–200 km) |
| `tylko_dla_niesamodzielnych` | boolean | ✅ | Filtruj do miejsc `przyjmuje_niesamodzielnych = true` |

**Response 200:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "podopieczni_do_przyjecia": 55,
  "miejsca_relokacji": [
    {
      "kod": "REL-CHE-001",
      "nazwa": "Hala Sportowa MOSiR Chełm",
      "typ": "hala_sportowa",
      "odleglosc_km": 22.4,
      "pojemnosc_dostepna": 180,
      "przyjmuje_niesamodzielnych": true,
      "procent_wypelnienia_po_przyjęciu": 31
    },
    {
      "kod": "REL-LBL-003",
      "nazwa": "Szkoła Podstawowa nr 4 w Świdniku",
      "typ": "szkola",
      "odleglosc_km": 41.0,
      "pojemnosc_dostepna": 120,
      "przyjmuje_niesamodzielnych": false,
      "procent_wypelnienia_po_przyjecia": 46
    }
  ],
  "lacznie_wolnych_miejsc": 300,
  "pokrycie": "wystarczajace"
}
```

Wartości pola `pokrycie`:

| Wartość | Znaczenie |
|---|---|
| `"wystarczajace"` | Łączna pojemność ≥ liczba ewakuowanych |
| `"ograniczone"` | Łączna pojemność < liczba ewakuowanych |
| `"brak"` | Brak miejsc relokacji w zadanym promieniu |

---

### `POST /api/calculate/threat`

Kalkulator zasięgu zagrożenia w czasie.
Odpowiada na pytanie: „Za ile godzin zagrożenie dotrze do tej placówki?"

**Body:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "typ_zagrozenia": "powodz",
  "szybkosc_wznoszenia_m_h": 0.25
}
```

| Pole | Typ | Wymagane | Opis |
|---|---|---|---|
| `placowka_kod` | string | ✅ | Kod placówki |
| `typ_zagrozenia` | string | ✅ | `powodz`, `pozar`, `blackout` |
| `szybkosc_wznoszenia_m_h` | number | Dla `powodz` | Prędkość wznoszenia wody [m/h] |
| `czas_baterii_generatora_h` | number | Dla `blackout` | Czas pracy generatora [h] |

**Response 200 — powódź:**

```json
{
  "placowka_kod": "DPS-CHE-002",
  "placowka_nazwa": "Dom Pomocy Społecznej w Sawinie",
  "typ_zagrozenia": "powodz",
  "status_zagrozenia": "w_strefie",
  "czas_do_zagrozenia_h": null,
  "komentarz": "Placówka już znajduje się w strefie zagrożenia powodziowego",
  "rekomendacja": "Natychmiastowa ewakuacja — IKE = 0.89",
  "obliczone_o": "2026-04-14T09:00:00Z"
}
```

**Response 200 — placówka poza strefą, szacowany czas:**

```json
{
  "placowka_kod": "DPS-LBL-001",
  "placowka_nazwa": "Dom Pomocy Społecznej im. Jana Pawła II w Niemcach",
  "typ_zagrozenia": "powodz",
  "status_zagrozenia": "poza_strefa",
  "odleglosc_do_granicy_strefy_km": 4.2,
  "czas_do_zagrozenia_h": 16.8,
  "komentarz": "Przy prędkości wznoszenia 0.25 m/h i odległości 4.2 km szacowany czas: 16.8 h",
  "rekomendacja": "Przygotowanie do ewakuacji — czas na działanie",
  "obliczone_o": "2026-04-14T09:00:00Z"
}
```

Wartości pola `status_zagrozenia`:

| Wartość | Znaczenie |
|---|---|
| `"w_strefie"` | Placówka już leży w strefie zagrożenia |
| `"poza_strefa"` | Poza strefą — podano szacowany czas |
| `"brak_danych_strefy"` | Warstwa zagrożeń niedostępna |

---

### `POST /api/scraper/run`

Uruchamia scraper danych z publicznie dostępnych źródeł urzędowych.
Operacja asynchroniczna — zwraca natychmiast, scraping trwa w tle.

**Body:** brak (puste)

**Response 202 Accepted:**

```json
{
  "status": "started",
  "job_id": "scrape-2026-04-14-090000",
  "zrodla": [
    "mpips.gov.pl/rejestr-placowek",
    "bip.lubelskie.pl/dps"
  ],
  "szacowany_czas_s": 120
}
```

**Response 409 (scraping już trwa):**
```json
{
  "error": "Scraping jest już w toku (job_id: scrape-2026-04-14-085500). Poczekaj na zakończenie.",
  "code": "SCRAPE_IN_PROGRESS",
  "timestamp": "2026-04-14T09:00:00Z"
}
```

---

### `GET /api/scraper/log`

Zwraca log ostatniego uruchomionego scrapingu.

**Parametry query (opcjonalne):**

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `job_id` | string | ostatni | ID joba do pobrania |

**Response 200:**

```json
{
  "job_id": "scrape-2026-04-14-090000",
  "status": "completed",
  "rozpoczeto": "2026-04-14T09:00:00Z",
  "zakończono": "2026-04-14T09:01:48Z",
  "czas_trwania_s": 108,
  "wyniki": {
    "pobrano_rekordow": 34,
    "zaktualizowano": 12,
    "dodano_nowych": 2,
    "bledow": 1
  },
  "bledy": [
    {
      "zrodlo": "bip.powiat-chelm.pl",
      "komunikat": "HTTP 503 — strona niedostępna",
      "timestamp": "2026-04-14T09:01:10Z"
    }
  ],
  "zrodla_przetworzone": [
    {
      "url": "https://mpips.gov.pl/rejestr-placowek",
      "format": "HTML",
      "rekordow_pobranych": 34,
      "status": "ok"
    }
  ]
}
```

---

### `GET /api/social/feed`

Zwraca feed sygnałów z social mediów (mock lub live).
Używany przez `SocialMediaPanel.jsx` i `SocialMediaPin.jsx`.

**Parametry query (opcjonalne):**

| Parametr | Typ | Domyślnie | Opis |
|---|---|---|---|
| `limit` | integer | 20 | Liczba postów do zwrócenia |
| `od` | string | — | ISO 8601 — posty od tej daty |
| `slowo_kluczowe` | string | — | Filtruj po słowie kluczowym |
| `powiat` | string | — | Filtruj do powiatu (na podstawie geolokalizacji) |

**Przykład żądania:**
```
GET /api/social/feed?limit=10&slowo_kluczowe=powodz
```

**Response 200:**

```json
{
  "ostatnia_aktualizacja": "2026-04-14T09:25:00Z",
  "liczba_wynikow": 2,
  "tryb": "mock",
  "posty": [
    {
      "id": "SM-001",
      "platforma": "twitter",
      "uzytkownik": "@mieszkaniec_lbl",
      "tresc": "Woda już wchodzi do piwnic przy ul. Nadrzecznej w Annopolu! Kilka rodzin prosi o pomoc.",
      "data": "2026-04-14T09:23:00Z",
      "lat": 50.8901,
      "lon": 21.8570,
      "toponimy": ["Annopol", "ul. Nadrzeczna"],
      "slowa_kluczowe": ["woda", "powodz", "pomoc"],
      "pewnosc_geolokalizacji": 0.91,
      "zweryfikowany": false
    },
    {
      "id": "SM-007",
      "platforma": "facebook",
      "uzytkownik": "Jan Kowalski",
      "tresc": "Droga przez Sawin całkowicie zalana, nie ma przejazdu w kierunku Chełma.",
      "data": "2026-04-14T09:18:00Z",
      "lat": 51.2108,
      "lon": 23.3984,
      "toponimy": ["Sawin", "Chełm"],
      "slowa_kluczowe": ["droga", "zalana"],
      "pewnosc_geolokalizacji": 0.88,
      "zweryfikowany": true
    }
  ]
}
```

Pole `tryb`:

| Wartość | Znaczenie |
|---|---|
| `"mock"` | Dane z `social_media_mock.json` (demonstracja) |
| `"live"` | Dane z prawdziwego API (produkcja) |

---

## WebSocket — live feed

Backend publikuje aktualizacje warstw przez STOMP over SockJS.

**URL połączenia:** `ws://localhost:8080/ws` (dev)

**Topiki subskrypcji:**

| Topik | Kiedy publikowany | Payload |
|---|---|---|
| `/topic/layers/{id}` | Po odświeżeniu warstwy przez `LayerRefreshScheduler` | Pełny GeoJSON warstwy |
| `/topic/ike` | Po przeliczeniu IKE przez `IkeService` | Lista `IkeResult` |
| `/topic/social` | Po pobraniu nowych postów przez `SocialMediaService` | Lista nowych postów |
| `/topic/system` | Zdarzenia systemowe (status scrapera, błędy) | Obiekt zdarzenia |

**Przykład payloadu `/topic/system`:**

```json
{
  "typ": "SCRAPER_COMPLETED",
  "komunikat": "Scraping zakończony: 34 rekordów pobranych",
  "timestamp": "2026-04-14T09:01:48Z",
  "dane": {
    "job_id": "scrape-2026-04-14-090000",
    "nowe_rekordy": 2
  }
}
```

Wartości pola `typ` dla `/topic/system`:

| Typ | Znaczenie |
|---|---|
| `SCRAPER_STARTED` | Scraper uruchomiony |
| `SCRAPER_COMPLETED` | Scraper zakończył działanie |
| `SCRAPER_ERROR` | Błąd scrapera |
| `IKE_RECALCULATED` | IKE przeliczone — odśwież dane |
| `LAYER_UPDATED` | Warstwa zaktualizowana (redundantne z `/topic/layers/{id}`) |
| `SYSTEM_WARNING` | Ostrzeżenie systemowe (np. OSRM niedostępny) |

---

## Mapowanie endpointów → komponenty React

| Endpoint | Komponent / Hook React |
|---|---|
| `GET /api/layers` | `useLayerData.js`, `LayerControlPanel.jsx` |
| `GET /api/layers/{id}` | `useLayerData.js`, każdy `*Layer.jsx` |
| `GET /api/ike` | `Top10Panel.jsx`, `DPSLayer.jsx` (kolorowanie markerów) |
| `GET /api/ike/{kod}` | `DPSPopup.jsx` (po kliknięciu markera) |
| `POST /api/ike/recalculate` | Przycisk „Przelicz IKE" w `LayerControlPanel.jsx` |
| `GET /api/ike/config` | `IKEScore.jsx` (legenda) |
| `POST /api/calculate/transport` | `TransportCalculator.jsx` |
| `POST /api/calculate/relocation` | `RelocationCalculator.jsx` |
| `POST /api/calculate/threat` | `ThreatSpreadCalculator.jsx` |
| `POST /api/scraper/run` | Przycisk „Odśwież dane z urzędów" w `LayerControlPanel.jsx` |
| `GET /api/scraper/log` | Modal logu w `LayerControlPanel.jsx` |
| `GET /api/social/feed` | `useSocialMediaFeed.js`, `SocialMediaPanel.jsx` |
| WebSocket `/topic/layers/{id}` | `useWebSocket.js` |
| WebSocket `/topic/ike` | `useWebSocket.js` → aktualizacja `Top10Panel` |
| WebSocket `/topic/system` | `Header.jsx` (wskaźnik statusu systemu) |
