# IKE_ALGORITHM.md — Algorytm Indeksu Krytyczności Ewakuacji

> Dokument referencyjny dla `IkeAgent.java` i `ike.config.json`.
> **Czytaj przed implementacją `IkeAgent.java`.**

---

## Spis treści

1. [Cel algorytmu](#1-cel-algorytmu)
2. [Wyzwalanie — event-driven](#2-wyzwalanie--event-driven)
3. [Formuła główna](#3-formuła-główna)
4. [Obliczanie składowych score'ów](#4-obliczanie-składowych-scoreów)
5. [Progi kategorii IKE](#5-progi-kategorii-ike)
6. [Edge case'y — tabela decyzyjna](#6-edge-casely--tabela-decyzyjna)
7. [Przykłady obliczenia IKE](#7-przykłady-obliczenia-ike)
8. [Wynik IKE — struktura DTO](#8-wynik-ike--struktura-dto)
9. [Konfiguracja wag](#9-konfiguracja-wag)
10. [Zapytania PostGIS](#10-zapytania-postgis)
11. [Kolejność kroków w IkeAgent.java](#11-kolejność-kroków-w-ikeagentjava)

---

## 1. Cel algorytmu

IKE przypisuje każdej placówce liczbę z zakresu **[0.0 – 1.0]**, gdzie:

- `1.0` = placówka w bezpośrednim zagrożeniu, bez transportu, pełna niesamodzielnych
- `0.0` = placówka bezpieczna, zasoby dostępne, poza strefą zagrożenia

Wynik IKE służy do:
- kolorowania markerów na mapie (czerwony / żółty / zielony)
- sortowania „Top 10 do ewakuacji" w `Top10Panel.jsx`
- wejścia dla `DecisionAgent` do generowania rekomendacji ewakuacyjnych

---

## 2. Wyzwalanie — event-driven

**IKE nie jest wywoływane ręcznie przez operatora ani bezpośrednio przez kontroler.**

`IkeAgent` jest `@EventListener` nasłuchującym na `ThreatUpdatedEvent`:

```java
@Service
public class IkeAgent {

    @Async("agentTaskExecutor")
    @EventListener
    public void onThreatUpdated(ThreatUpdatedEvent event) {
        log.info("[IkeAgent] Start przeliczania IKE, correlation_id={}",
                 event.getCorrelationId());
        recalculateAll(event.getCorrelationId());
    }
}
```

**Flow w kontekście event-driven:**

```
FloodImportAgent zapisuje strefy do bazy
    → publishEvent(ThreatUpdatedEvent)      [wątek HTTP kończy się tutaj]

    [wątek agenta] IkeAgent.onThreatUpdated()
        → oblicza IKE dla wszystkich 48 placówek
        → zapisuje wyniki do ike_results (z correlation_id)
        → publishEvent(IkeRecalculatedEvent)

    [wątek agenta] DecisionAgent.onIkeRecalculated()
        → generuje rekomendacje ewakuacyjne

    [wątek agenta] LiveFeedService.onIkeRecalculated()
        → pushuje /topic/ike przez WebSocket
```

**Jedyna droga do wywołania IKE z zewnątrz:**
`POST /api/ike/recalculate` → `ThreatController` → `publisher.publishEvent(new ThreatUpdatedEvent(...))`
Kontroler nie wywołuje `IkeAgent` bezpośrednio.

---

## 3. Formuła główna

```
IKE = (W_zag  × score_zagrozenia)
    + (W_nies × score_niesamodzielnych)
    + (W_tran × score_braku_transportu)
    + (W_droz × score_braku_droznosci)
    + (W_odl  × score_odleglosci_relokacji)
```

Wagi domyślne (z `ike.config.json`):

| Symbol | Nazwa | Waga |
|---|---|---|
| W_zag | Zagrożenie strefą kryzysową | **0.35** |
| W_nies | Udział niesamodzielnych | **0.25** |
| W_tran | Brak dostępnego transportu | **0.20** |
| W_droz | Brak drożności dróg | **0.15** |
| W_odl | Odległość do miejsca relokacji | **0.05** |
| | **Suma** | **1.00** |

Wagi wczytywane przez `IkeAgent` przy starcie (`@PostConstruct`).
Zmiana wag nie wymaga rekompilacji — tylko restartu backendu lub `POST /api/ike/recalculate`.

**Walidacja przy starcie:**

```java
@PostConstruct
void validateWeights() {
    double sum = wagi.zagrozenie + wagi.niesamodzielni + wagi.transportBrak
               + wagi.droznoscBrak + wagi.odlegloscRelokacji;
    if (Math.abs(sum - 1.0) > 0.001) {
        throw new IllegalStateException(
            "Suma wag IKE ≠ 1.0 (wartość: " + sum + "). Sprawdź ike.config.json."
        );
    }
}
```

---

## 4. Obliczanie składowych score'ów

Każdy score: **[0.0 – 1.0]**, gdzie `1.0` = stan najbardziej krytyczny.

### 4.1 `score_zagrozenia`

**Zapytanie:** `ST_Intersects(placowka.geom, strefa.geom)` na tabeli `strefy_zagrozen`

```
Strefa czerwona (poziom = 'czerwony') → score = 1.0
Strefa żółta   (poziom = 'zolty')    → score = 0.60
Strefa zielona (poziom = 'zielony')  → score = 0.20
Poza strefą                          → score = 0.0
Tabela strefy_zagrozen pusta         → score = 0.0  [domyślna po ThreatClear]
L-03 niedostępna (błąd BD)           → score = 0.0 + WARN
```

Przy nakładaniu wielu stref: weź **maksymalny** score.

### 4.2 `score_niesamodzielnych`

```
score = placowka.niesamodzielni_procent

NULL → score = 0.50 + WARN
> 1.0 → score = 1.0 + WARN
```

### 4.3 `score_braku_transportu`

**Zapytanie:** `ST_DWithin` w EPSG:2180 (metry), promień z konfiguracji (15 km = 15 000 m)

Zliczaj tylko `dostepny = TRUE`. Jeśli `niesamodzielni_procent > 0.5` — zliczaj
tylko `przyjmuje_niesamodzielnych = TRUE`.

```
N = 0 → score = 1.0
N = 1 → score = 0.75
N = 2 → score = 0.50
N = 3 → score = 0.25
N ≥ 4 → score = 0.0
L-05 niedostępna → score = 1.0 + WARN
```

### 4.4 `score_braku_droznosci`

**Zapytanie:** drogi w promieniu 2 km od placówki, grupowane po `droznosc`

```
Wszystkie zablokowane                      → score = 1.0
Część zablokowana + część z utrudnieniami  → score = 0.70
Część zablokowana + część przejezdna       → score = 0.50
Wszystkie z utrudnieniami (bez zablokow.)  → score = 0.30
Co najmniej jedna przejezdna               → score = 0.0
Brak dróg w promieniu / L-04 offline       → score = 0.5 + WARN
```

### 4.5 `score_odleglosci_relokacji`

**Zapytanie:** `ST_Distance` w EPSG:2180 do najbliższego `pojemnosc_dostepna > 0`

```
d ≤ 10 km        → score = 0.0
10 < d ≤ 25 km   → score = 0.25
25 < d ≤ 40 km   → score = 0.50
40 < d ≤ 50 km   → score = 0.75
d > 50 km        → IKE = null (wyklucz z Top10)
Brak miejsc      → IKE = null (wyklucz z Top10)
```

---

## 5. Progi kategorii IKE

| Zakres | Kategoria | Kolor | Hex | Akcja |
|---|---|---|---|---|
| 0.70–1.00 | `czerwony` | 🔴 | `#EF4444` | Ewakuacja natychmiastowa |
| 0.40–0.69 | `zolty` | 🟡 | `#F59E0B` | Przygotowanie do ewakuacji |
| 0.00–0.39 | `zielony` | 🟢 | `#22C55E` | Monitoring |
| `null` | `nieznany` | ⚫ | `#6B7280` | Brak danych — wyklucz z Top10 |

---

## 6. Edge case'y — tabela decyzyjna

| # | Sytuacja | Zachowanie | Log |
|---|---|---|---|
| E1 | `liczba_podopiecznych = 0` | IKE = 0.0, kategoria `zielony` | INFO |
| E2 | `niesamodzielni_procent` = NULL | score = 0.50 | WARN |
| E3 | Tabela `strefy_zagrozen` pusta (po ThreatClear) | `score_zagrozenia = 0.0` | INFO |
| E4 | Warstwa transportu niedostępna (błąd BD) | `score_braku_transportu = 1.0` | WARN |
| E5 | Warstwa dróg niedostępna | `score_braku_droznosci = 0.5` | WARN |
| E6 | Brak miejsc relokacji w 50 km | IKE = null, wyklucz z Top10 | WARN |
| E7 | Placówka w wielu strefach jednocześnie | weź max `score_zagrozenia` | DEBUG |
| E8 | `pojemnosc_ogolna` = NULL lub 0 | pomiń placówkę | ERROR |
| E9 | `geom` placówki NULL | pomiń placówkę | ERROR |
| E10 | Suma wag ≠ 1.0 | rzuć `IllegalStateException` przy starcie | ERROR |
| E11 | Wszystkie miejsca relokacji mają `pojemnosc_dostepna = 0` | IKE = null dla wszystkich | ERROR |
| E12 | `niesamodzielni_procent > 1.0` | score = 1.0 | WARN |
| E13 | Recalculate wywołane podczas trwającego obliczania | odrzuć, zwróć 409 | WARN |
| E14 | `ThreatUpdatedEvent` z pustą listą stref (po ThreatClear) | przelicz normalnie — score_zagrozenia = 0.0 dla wszystkich | INFO |

---

## 7. Przykłady obliczenia IKE

### Przykład A — DPS-CHE-002 (Sawin) — wynik czerwony

Scenariusz: powódź Q100 dla powiatu chełmskiego (po `ThreatUpdatedEvent`).

```
score_zagrozenia           = 1.00  (strefa czerwona Q100)
score_niesamodzielnych     = 0.89  (niesamodzielni_procent)
score_braku_transportu     = 1.00  (N=0, strefa odcięta)
score_braku_droznosci      = 0.70  (zablokowana + utrudnienia)
score_odleglosci_relokacji = 0.25  (22 km)

IKE = (0.35×1.00) + (0.25×0.89) + (0.20×1.00) + (0.15×0.70) + (0.05×0.25)
IKE = 0.3500 + 0.2225 + 0.2000 + 0.1050 + 0.0125 = 0.8900
```

**→ IKE = 0.89 → czerwony → DecisionAgent: `ewakuuj_natychmiast`**

---

### Przykład B — DPS-LBL-001 (Niemce) — wynik żółty

```
score_zagrozenia           = 0.60  (strefa żółta)
score_niesamodzielnych     = 0.68
score_braku_transportu     = 0.50  (N=2)
score_braku_droznosci      = 0.00  (drogi przejezdne)
score_odleglosci_relokacji = 0.00  (8 km)

IKE = (0.35×0.60) + (0.25×0.68) + (0.20×0.50) + (0.15×0.00) + (0.05×0.00)
IKE = 0.2100 + 0.1700 + 0.1000 + 0.0000 + 0.0000 = 0.4800
```

**→ IKE = 0.48 → żółty → DecisionAgent: `przygotuj_ewakuacje`**

---

### Przykład C — DPS-LUK-002 (Stoczek Łukowski) — wynik zielony

Scenariusz: `ThreatClear` — brak aktywnych stref.

```
score_zagrozenia           = 0.00  (brak stref po ThreatClear)
score_niesamodzielnych     = 0.20
score_braku_transportu     = 0.00  (N≥4)
score_braku_droznosci      = 0.00  (przejezdne)
score_odleglosci_relokacji = 0.25  (12 km)

IKE = 0.0000 + 0.0500 + 0.0000 + 0.0000 + 0.0125 = 0.0625
```

**→ IKE = 0.06 → zielony → DecisionAgent: `monitoruj`**

---

### Przykład D — edge case'y E2 + E5

```
niesamodzielni_procent = NULL → score = 0.50 (E2, log WARN)
Warstwa dróg offline          → score = 0.50 (E5, log WARN)
Strefa czerwona               → score = 1.00
N=1 pojazd                    → score = 0.75
Odległość 35 km               → score = 0.50

IKE = (0.35×1.00) + (0.25×0.50) + (0.20×0.75) + (0.15×0.50) + (0.05×0.50)
IKE = 0.3500 + 0.1250 + 0.1500 + 0.0750 + 0.0250 = 0.7250
```

**→ IKE = 0.73 → czerwony**
DTO zawiera `data_warnings: ["niesamodzielni_procent_brak", "warstwa_drogi_offline"]`

---

## 8. Wynik IKE — struktura DTO

```json
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
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "data_warnings": [],
  "obliczone_o": "2026-04-14T09:00:00Z"
}
```

Gdy IKE = null (E6/E11):

```json
{
  "placowka_kod": "DPS-VLO-002",
  "ike_score": null,
  "ike_kategoria": "nieznany",
  "cel_relokacji": null,
  "data_warnings": ["brak_miejsca_relokacji_w_promieniu_50km"],
  "obliczone_o": "2026-04-14T09:00:00Z"
}
```

---

## 9. Konfiguracja wag

Patrz `backend/src/main/resources/ike.config.json` i `docs/DATA_SCHEMA.md` sekcja 8.

Plik leży po stronie backendu i wczytywany jest przez `IkeAgent` przez `ClassPathResource`:

```java
@PostConstruct
void loadConfig() {
    ClassPathResource res = new ClassPathResource("ike.config.json");
    IkeConfig config = objectMapper.readValue(res.getInputStream(), IkeConfig.class);
    this.wagi = config.getWagi();
    // walidacja sumy wag ...
}
```

Frontend pobiera aktualną konfigurację wyłącznie przez `GET /api/ike/config` — nie czyta pliku bezpośrednio.

Zmiana wag → restart backendu lub `POST /api/ike/recalculate`
(publikuje `ThreatUpdatedEvent` z bieżącymi strefami).

---

## 10. Zapytania PostGIS

Wszystkie zapytania używają EPSG:2180 dla dokładnych pomiarów metrycznych.

### 10.1 Sprawdzenie strefy zagrożenia

```sql
SELECT s.poziom, s.typ_zagrozenia
FROM strefy_zagrozen s
WHERE ST_Intersects(
    ST_Transform(
        (SELECT geom FROM placowka WHERE kod = :placowkaKod),
        2180
    ),
    ST_Transform(s.geom, 2180)
)
ORDER BY CASE s.poziom
    WHEN 'czerwony' THEN 1
    WHEN 'zolty'    THEN 2
    WHEN 'zielony'  THEN 3
END
LIMIT 1;
```

### 10.2 Liczba pojazdów w promieniu

```sql
SELECT COUNT(*) as n
FROM zasob_transportu t
WHERE t.dostepny = TRUE
  AND (:wymagaPrzystosowania = FALSE OR t.przyjmuje_niesamodzielnych = TRUE)
  AND ST_DWithin(
      ST_Transform(t.geom, 2180),
      ST_Transform(
          (SELECT geom FROM placowka WHERE kod = :placowkaKod),
          2180
      ),
      :promienMetry
  );
```

### 10.3 Stan dróg w promieniu 2 km

```sql
SELECT d.droznosc, COUNT(*) as liczba
FROM drogi_ewakuacyjne d
WHERE ST_DWithin(
    ST_Transform(d.geom, 2180),
    ST_Transform(
        (SELECT geom FROM placowka WHERE kod = :placowkaKod),
        2180
    ),
    2000
)
GROUP BY d.droznosc;
```

### 10.4 Najbliższe miejsce relokacji

```sql
SELECT r.kod, r.nazwa, r.przyjmuje_niesamodzielnych, r.pojemnosc_dostepna,
    ST_Distance(
        ST_Transform(r.geom, 2180),
        ST_Transform(
            (SELECT geom FROM placowka WHERE kod = :placowkaKod),
            2180
        )
    ) / 1000.0 AS odleglosc_km
FROM miejsca_relokacji r
WHERE r.pojemnosc_dostepna > 0
ORDER BY odleglosc_km ASC
LIMIT 1;
```

---

## 11. Kolejność kroków w IkeAgent.java

```
FUNKCJA recalculateAll(correlationId):

  1. Pobierz wszystkie placówki z bazy.

  2. Dla każdej placówki wywołaj obliczIke(placowka, correlationId):

      a. Sprawdź warunki dyskwalifikujące (geom IS NULL, pojemnosc = 0)
         → pomiń, zaloguj ERROR

      b. Sprawdź warunek zerowania (liczba_podopiecznych = 0)
         → IKE = 0.0, kategoria zielony

      c. Oblicz score_zagrozenia (zapytanie 10.1)
         → jeśli brak stref (ThreatClear): score = 0.0

      d. Oblicz score_niesamodzielnych
         → NULL: score = 0.50, WARN

      e. Oblicz score_braku_transportu (zapytanie 10.2)
         → użyj wariantu z przyjmuje_niesamodzielnych jeśli score_nies > 0.5

      f. Oblicz score_braku_droznosci (zapytanie 10.3)
         → brak dróg / offline: score = 0.5, WARN

      g. Znajdź najbliższe miejsce relokacji (zapytanie 10.4)
         → brak w 50 km: IKE = null, WARN

      h. Oblicz score_odleglosci_relokacji

      i. Oblicz IKE = suma ważona, zaokrągl do 4 miejsc po przecinku

      j. Wyznacz kategorię (czerwony / zolty / zielony / nieznany)

      k. Pobierz trasę z OSRM
         → OSRM offline: trasa = null, nie blokuj wyniku IKE

      l. Upsert do ike_results z correlation_id — zapisz wszystkie pięć kolumn
         score_* (mogą być NULL jeśli obliczenie nie było możliwe) oraz data_warnings

  3. Po przetworzeniu wszystkich placówek:
     publisher.publishEvent(new IkeRecalculatedEvent(correlationId))

  4. Zaloguj: "[IkeAgent] Zakończono, correlation_id={}, placówki={}, czas={}ms"
```
